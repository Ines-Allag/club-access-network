import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
 
/**
 * Serveur du Club de Sport.
 *
 * Reçoit les connexions TCP des bornes (borne 1 : contrôle d'accès,
 * borne 2 : salle de séance) et répond selon le protocole applicatif :
 * un message = une ligne texte terminée par '\n'.
 *
 * Compilation : javac Serveur.java
 * Lancement   : java Serveur [port]      (port par défaut : 9000)
 *
 * Les données (bornes, adhérents) sont ici SIMULÉES en mémoire.
 * Plus tard, il suffira de remplacer les méthodes de la classe "Donnees"
 * par des requêtes SQL (JDBC) vers la vraie base.
 */
public class Serveur {
 
    static final int PORT_PAR_DEFAUT = 9000;
    static final int TIMEOUT_LECTURE_MS = 30_000;   // un client muet est déconnecté
    static final int TAILLE_MAX_LIGNE = 1024;       // on refuse les lignes démesurées
    static final Pattern MOTIF_ID_ADHERENT = Pattern.compile("^[A-Za-z0-9]{12}$");
 
    // ------------------------------------------------------------------
    // 1) Les données (simulation de la base de données)
    // ------------------------------------------------------------------
    enum TypeBorne { CONTROLE_ACCES, SALLE_SEANCE }
 
    enum Abonnement { VALIDE, DELAI_GRACE, EXPIRE }
 
    static class Borne {
        final int id;
        final TypeBorne type;
        final boolean active;
 
        Borne(int id, TypeBorne type, boolean active) {
            this.id = id;
            this.type = type;
            this.active = active;
        }
    }
 
    static class Adherent {
        final String id;
        final Abonnement abonnement;
        final boolean reservation;   // a une séance collective réservée sur ce créneau
 
        Adherent(String id, Abonnement abonnement, boolean reservation) {
            this.id = id;
            this.abonnement = abonnement;
            this.reservation = reservation;
        }
    }
 
    static class Donnees {
        static final Map<Integer, Borne> BORNES = new ConcurrentHashMap<>();
        static final Map<String, Adherent> ADHERENTS = new ConcurrentHashMap<>();
        // adhérents actuellement dans le club : id -> heure d'entrée
        static final Map<String, LocalDateTime> PRESENTS = new ConcurrentHashMap<>();
 
        static {
            BORNES.put(1, new Borne(1, TypeBorne.CONTROLE_ACCES, true));
            BORNES.put(2, new Borne(2, TypeBorne.SALLE_SEANCE, true));
            BORNES.put(3, new Borne(3, TypeBorne.CONTROLE_ACCES, false));   // désactivée
 
            ajouter("ADH000000001", Abonnement.VALIDE, true);        // cas nominal
            ajouter("ADH000000002", Abonnement.DELAI_GRACE, true);   // délai de grâce
            ajouter("ADH000000003", Abonnement.EXPIRE, false);       // accès refusé
            ajouter("ADH000000004", Abonnement.VALIDE, false);       // pas de réservation
        }
 
        static void ajouter(String id, Abonnement a, boolean reservation) {
            ADHERENTS.put(id, new Adherent(id, a, reservation));
        }
    }
 
    // ------------------------------------------------------------------
    // 2) Le traitement d'UNE connexion (une instance par client)
    // ------------------------------------------------------------------
    static class Session implements Runnable {
        private final Socket socket;
        private OutputStream sortie;
 
        // état de la conversation avec CE client
        private Borne borne = null;                 // rempli par IDENTIFIER
        private Adherent enAttenteDelaiGrace = null;
        private Adherent adherentCourant = null;    // adhérent dont l'accès vient d'être accepté
 
        Session(Socket socket) {
            this.socket = socket;
        }
 
        @Override
        public void run() {
            String client = socket.getRemoteSocketAddress().toString();
            log("connexion de " + client);
            try (Socket s = socket;
                 BufferedReader entree = new BufferedReader(
                         new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8))) {
 
                s.setSoTimeout(TIMEOUT_LECTURE_MS);
                sortie = s.getOutputStream();
 
                String ligne;
                while ((ligne = entree.readLine()) != null) {
                    ligne = ligne.trim();
                    if (ligne.isEmpty()) {
                        continue;
                    }
                    if (ligne.length() > TAILLE_MAX_LIGNE) {
                        repondre("ERREUR ligne_trop_longue");
                        break;
                    }
                    log("<- " + ligne);
                    boolean continuer = traiter(ligne);
                    if (!continuer) {
                        break;
                    }
                }
            } catch (SocketTimeoutException e) {
                log("délai dépassé : client déconnecté");
            } catch (IOException e) {
                log("erreur réseau : " + e.getMessage());
            }
            log("connexion fermée (" + client + ")");
        }
 
        /** Envoie une ligne au client. */
        private void repondre(String message) throws IOException {
            log("-> " + message);
            sortie.write((message + "\n").getBytes(StandardCharsets.UTF_8));
            sortie.flush();
        }
 
        /** Analyse une commande. Retourne false pour fermer la connexion. */
        private boolean traiter(String ligne) throws IOException {
            String[] mots = ligne.split(" ");
            String commande = mots[0];
 
            if (commande.equals("IDENTIFIER")) {
                return traiterIdentifier(mots);
            }
 
            // Toute autre commande exige d'avoir été identifié d'abord
            if (borne == null) {
                repondre("ERREUR identification_requise");
                return true;
            }
 
            switch (commande) {
                case "ACCES":
                    return traiterAcces(mots);
                case "DECISION_DELAI_GRACE":
                    return traiterDecisionDelaiGrace(mots);
                case "ACCES_ETAGE":
                    return traiterAccesEtage();
                case "SORTIE":
                    return traiterSortie(mots);
                case "ENTREE_SALLE":
                    return traiterEntreeSalle(mots);
                default:
                    repondre("ERREUR commande_inconnue");
                    return true;
            }
        }
 
        // ---------------- IDENTIFIER idBorne ----------------
        private boolean traiterIdentifier(String[] mots) throws IOException {
            if (mots.length != 2) {
                repondre("ERREUR syntaxe");
                return true;
            }
            int id;
            try {
                id = Integer.parseInt(mots[1]);
            } catch (NumberFormatException e) {
                repondre("ERREUR syntaxe");
                return true;
            }
            Borne b = Donnees.BORNES.get(id);
            if (b == null) {
                repondre("BORNE_INCONNUE");
                return false;                      // plus rien à faire : on ferme
            }
            if (!b.active) {
                repondre("BORNE_INACTIVE");
                return false;
            }
            borne = b;
            repondre("BORNE_OK");
            return true;
        }
 
        // ---------------- ACCES id_adherent (borne 1) ----------------
        private boolean traiterAcces(String[] mots) throws IOException {
            if (!verifierType(TypeBorne.CONTROLE_ACCES) || !verifierIdAdherent(mots, 2)) {
                return true;
            }
            Adherent a = Donnees.ADHERENTS.get(mots[1]);
            if (a == null) {
                repondre("ACCES_REFUSE motif=adherent_inconnu");
                return true;
            }
            switch (a.abonnement) {
                case VALIDE:
                    entrerDansLeClub(a);
                    repondre("ACCES_AUTORISE");
                    break;
                case DELAI_GRACE:
                    enAttenteDelaiGrace = a;       // on attend la décision de l'adhérent
                    repondre("DELAI_GRACE_PROPOSE");
                    break;
                default:                           // EXPIRE
                    repondre("ACCES_REFUSE motif=abonnement");
            }
            return true;
        }
 
        // ---------------- DECISION_DELAI_GRACE oui|non ----------------
        private boolean traiterDecisionDelaiGrace(String[] mots) throws IOException {
            if (!verifierType(TypeBorne.CONTROLE_ACCES)) {
                return true;
            }
            if (enAttenteDelaiGrace == null) {
                repondre("ERREUR sequence");       // aucune proposition en cours
                return true;
            }
            if (mots.length != 2 || !(mots[1].equals("oui") || mots[1].equals("non"))) {
                repondre("ERREUR syntaxe");
                return true;
            }
            Adherent a = enAttenteDelaiGrace;
            enAttenteDelaiGrace = null;
            if (mots[1].equals("oui")) {
                entrerDansLeClub(a);
                repondre("ACCES_AUTORISE_AVERTISSEMENT");
            } else {
                repondre("ACCES_REFUSE motif=abonnement");
            }
            return true;
        }
 
        // ---------------- ACCES_ETAGE (borne 1) ----------------
        private boolean traiterAccesEtage() throws IOException {
            if (!verifierType(TypeBorne.CONTROLE_ACCES)) {
                return true;
            }
            if (adherentCourant == null) {
                repondre("ERREUR sequence");       // personne n'a été autorisé à entrer
                return true;
            }
            repondre(adherentCourant.reservation ? "ETAGE_AUTORISE" : "ETAGE_VERROUILLE");
            return true;
        }
 
        // ---------------- SORTIE id_adherent horodatage (borne 1) ----------------
        private boolean traiterSortie(String[] mots) throws IOException {
            if (!verifierType(TypeBorne.CONTROLE_ACCES) || !verifierIdAdherent(mots, 3)) {
                return true;
            }
            if (!Donnees.ADHERENTS.containsKey(mots[1])) {
                repondre("ERREUR adherent_inconnu");
                return true;
            }
            LocalDateTime sortie;
            try {
                sortie = LocalDateTime.parse(mots[2]);   // format ISO : 2026-10-25T19:41:00
            } catch (DateTimeParseException e) {
                repondre("ERREUR horodatage_invalide");
                return true;
            }
            LocalDateTime entree = Donnees.PRESENTS.remove(mots[1]);
            Duration duree = (entree == null || sortie.isBefore(entree))
                    ? Duration.ZERO
                    : Duration.between(entree, sortie);
            repondre("SORTIE_ENREGISTREE " + formaterDuree(duree));
            return true;
        }
 
        // ---------------- ENTREE_SALLE id_adherent (borne 2) ----------------
        private boolean traiterEntreeSalle(String[] mots) throws IOException {
            if (!verifierType(TypeBorne.SALLE_SEANCE) || !verifierIdAdherent(mots, 2)) {
                return true;
            }
            Adherent a = Donnees.ADHERENTS.get(mots[1]);
            repondre(a != null && a.reservation ? "SALLE_AUTORISEE" : "SALLE_REFUSEE");
            return true;
        }
 
        // ---------------- utilitaires ----------------
        private void entrerDansLeClub(Adherent a) {
            adherentCourant = a;
            Donnees.PRESENTS.put(a.id, LocalDateTime.now());
        }
 
        /** Vérifie que la borne identifiée a le droit d'utiliser cette commande. */
        private boolean verifierType(TypeBorne attendu) throws IOException {
            if (borne.type != attendu) {
                repondre("ERREUR commande_non_autorisee");
                return false;
            }
            return true;
        }
 
        /** Vérifie le nombre de mots et le format de l'id_adherent (mots[1]). */
        private boolean verifierIdAdherent(String[] mots, int nbMotsAttendus) throws IOException {
            if (mots.length != nbMotsAttendus || !MOTIF_ID_ADHERENT.matcher(mots[1]).matches()) {
                repondre("ERREUR syntaxe");
                return false;
            }
            return true;
        }
 
        private static String formaterDuree(Duration d) {
            long s = d.getSeconds();
            return String.format("%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
        }
 
        private void log(String texte) {
            System.out.println("[" + Thread.currentThread().getName() + "] " + texte);
        }
    }
 
    // ------------------------------------------------------------------
    // 3) Le programme principal : accepter les clients
    // ------------------------------------------------------------------
    public static void main(String[] args) throws IOException {
        int port = PORT_PAR_DEFAUT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Port invalide : " + args[0]);
                System.exit(1);
            }
        }
 
        ExecutorService pool = Executors.newFixedThreadPool(20);   // max 20 clients en parallèle
 
        try (ServerSocket serveur = new ServerSocket()) {
            serveur.setReuseAddress(true);
            serveur.bind(new InetSocketAddress(port));
            System.out.println("Serveur en écoute sur le port " + port + " (Ctrl+C pour arrêter)");
 
            while (true) {
                Socket client = serveur.accept();        // bloque jusqu'à une connexion
                pool.execute(new Session(client));       // un thread s'occupe de ce client
            }
        } finally {
            pool.shutdown();
        }
    }
}

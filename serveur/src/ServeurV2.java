import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Serveur applicatif du Club de Sport  (version 2 : branché sur PostgreSQL).
 *
 * Les bornes (clients) se connectent en TCP et envoient des messages texte,
 * une ligne = un message. Le serveur interroge la base PostgreSQL (JDBC),
 * décide, puis répond. Le protocole est celui des diagrammes du rapport.
 *
 * Compilation : javac -encoding UTF-8 -d out src\Serveur.java
 * Lancement   : voir README.md (variables CLUB_DB_PASSWORD, etc.)
 */
public class Serveur {

    // ------------------------------------------------------------------
    // 0) Réglages (constantes)
    // ------------------------------------------------------------------
    static final int PORT_PAR_DEFAUT = 9000;

    // --- Protection contre les clients malveillants ou trop lents ---
    static final int TAILLE_MAX_LIGNE   = 1024;      // octets max par message
    static final int DELAI_AVANT_IDENT  = 20_000;    // ms pour envoyer IDENTIFIER
    static final int DELAI_INACTIVITE   = 60_000;    // ms de silence toléré ensuite
    static final int DELAI_LIGNE_MAX    = 5_000;     // ms max pour finir d'écrire UNE ligne
    static final int MAX_COMMANDES      = 100;       // messages max par connexion
    static final int MAX_ERREURS        = 10;        // erreurs max avant déconnexion
    static final int MAX_CLIENTS        = 20;        // threads en parallèle
    static final int FILE_ATTENTE       = 10;        // clients autorisés à attendre un thread libre
    static final int MAX_PAR_IP         = 8;         // connexions simultanées max pour une même IP

    // --- Règles métier ---
    static final int MINUTES_AVANT_SEANCE = 30;      // accès à l'étage / salle autorisé 30 min avant le début

    static final Pattern MOTIF_COMMANDE    = Pattern.compile("^([A-Za-z_]+)\\s*(.*)$");
    static final Pattern MOTIF_ID_ADHERENT = Pattern.compile("^[A-Za-z0-9]{12}$");
    static final Pattern MOTIF_ID_BORNE    = Pattern.compile("^[0-9]{1,9}$");

    // Nombre de connexions actuellement ouvertes, par adresse IP
    static final Map<String, AtomicInteger> CONNEXIONS_PAR_IP = new ConcurrentHashMap<>();

    // ------------------------------------------------------------------
    // 1) Configuration de la base (lue dans des variables d'environnement)
    //    -> aucun mot de passe écrit dans le code.
    // ------------------------------------------------------------------
    static final class Config {
        static final String URL = lire("CLUB_DB_URL",
                "jdbc:postgresql://localhost:5432/clubsport?connectTimeout=5&socketTimeout=10");
        static final String UTILISATEUR  = lire("CLUB_DB_USER", "club_serveur");
        static final String MOT_DE_PASSE = lire("CLUB_DB_PASSWORD", null);

        private static String lire(String nom, String defaut) {
            String v = System.getenv(nom);
            return (v == null || v.isBlank()) ? defaut : v;
        }
    }

    // ------------------------------------------------------------------
    // 2) Le temps : l'heure « actuelle » du serveur.
    //    Pour les tests, on peut la figer :  -Dclub.maintenant=2026-10-12T17:50:00
    //    (le jeu de données contient des séances en octobre 2026)
    // ------------------------------------------------------------------
    static LocalDateTime maintenant() {
        String fige = System.getProperty("club.maintenant");
        if (fige != null && !fige.isBlank()) {
            return LocalDateTime.parse(fige);
        }
        return LocalDateTime.now().withNano(0);
    }

    // ------------------------------------------------------------------
    // 3) Petits objets qui représentent ce que la base nous renvoie.
    //    « record » = une classe de données toute simple : Java fabrique
    //    seul le constructeur et les accesseurs (b.type(), b.active()...).
    // ------------------------------------------------------------------
    enum TypeBorne { CONTROLE_ACCES, SALLE_SEANCE }

    enum EtatAbonnement { VALIDE, DELAI_GRACE, REFUSE }

    record InfoBorne(int id, TypeBorne type, boolean active) { }

    record InfoAdherent(String etatBadge, String statutAdmin,
                        LocalDate finPenalite, LocalDate certificatExpire) { }

    record InfoAbonnement(String statut, LocalDate debut, LocalDate fin, int delaiGraceJours) { }

    // ------------------------------------------------------------------
    // 4) L'accès à PostgreSQL (JDBC). TOUTES les requêtes SQL sont ici.
    //    Une instance par client ; la connexion est ouverte à la demande
    //    et fermée avec la session.
    //    Les valeurs sont passées par des « ? » (PreparedStatement) :
    //    elles ne sont JAMAIS collées dans le texte SQL -> pas d'injection SQL.
    // ------------------------------------------------------------------
    static class BaseDeDonnees implements AutoCloseable {
        private Connection cx;

        private Connection connexion() throws SQLException {
            if (cx == null || cx.isClosed()) {
                cx = DriverManager.getConnection(Config.URL, Config.UTILISATEUR, Config.MOT_DE_PASSE);
            }
            return cx;
        }

        private PreparedStatement preparer(String sql) throws SQLException {
            PreparedStatement ps = connexion().prepareStatement(sql);
            ps.setQueryTimeout(5);
            return ps;
        }

        /** Q1 du rapport : la borne existe-t-elle ? (+ son type et son statut) */
        InfoBorne chercherBorne(int idBorne) throws SQLException {
            try (PreparedStatement ps = preparer(
                    "SELECT type_borne, statut FROM club.borne WHERE id_borne = ?")) {
                ps.setInt(1, idBorne);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return null;
                    }
                    TypeBorne type = switch (rs.getString("type_borne")) {
                        case "controle_acces" -> TypeBorne.CONTROLE_ACCES;
                        case "salle_seance"   -> TypeBorne.SALLE_SEANCE;
                        default -> null;
                    };
                    if (type == null) {
                        return null;
                    }
                    return new InfoBorne(idBorne, type, "active".equals(rs.getString("statut")));
                }
            }
        }

        /** Q2 et Q5 : l'adhérent existe-t-il ? quel est son statut administratif, son certificat ? */
        InfoAdherent chercherAdherent(String idAdherent) throws SQLException {
            try (PreparedStatement ps = preparer(
                    "SELECT etat_badge, statut_admin, fin_penalite, certif_medical_exp "
                  + "FROM club.adherent WHERE id_adherent = ?")) {
                ps.setString(1, idAdherent);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return null;
                    }
                    return new InfoAdherent(
                            rs.getString("etat_badge"),
                            rs.getString("statut_admin"),
                            rs.getObject("fin_penalite", LocalDate.class),
                            rs.getObject("certif_medical_exp", LocalDate.class));
                }
            }
        }

        /** Q3 : les abonnements de l'adhérent (c'est Java qui décide lequel est valable). */
        List<InfoAbonnement> chercherAbonnements(String idAdherent) throws SQLException {
            List<InfoAbonnement> liste = new ArrayList<>();
            try (PreparedStatement ps = preparer(
                    "SELECT statut, date_debut, date_fin, delai_grace_jours "
                  + "FROM club.abonnement WHERE id_adherent = ?")) {
                ps.setString(1, idAdherent);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        liste.add(new InfoAbonnement(
                                rs.getString("statut"),
                                rs.getObject("date_debut", LocalDate.class),
                                rs.getObject("date_fin", LocalDate.class),
                                rs.getInt("delai_grace_jours")));
                    }
                }
            }
            return liste;
        }

        /** Q7 : une session de présence est-elle encore ouverte (adhérent déjà dans le club) ? */
        boolean aUneSessionOuverte(String idAdherent) throws SQLException {
            return entreeSessionOuverte(idAdherent) != null;
        }

        /** Heure d'entrée de la session ouverte, ou null s'il n'y en a pas. */
        LocalDateTime entreeSessionOuverte(String idAdherent) throws SQLException {
            try (PreparedStatement ps = preparer(
                    "SELECT date_heure_entree FROM club.presence "
                  + "WHERE id_adherent = ? AND date_heure_sortie IS NULL "
                  + "ORDER BY date_heure_entree DESC LIMIT 1")) {
                ps.setString(1, idAdherent);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getObject(1, LocalDateTime.class) : null;
                }
            }
        }

        /** ÉCRITURE : ouvre une session de présence. Retourne false si une session est déjà ouverte. */
        boolean ouvrirPresence(String idAdherent, LocalDateTime entree, boolean delaiGrace) throws SQLException {
            try (PreparedStatement ps = preparer(
                    "INSERT INTO club.presence (id_adherent, date_heure_entree, delai_grace_utilise) "
                  + "VALUES (?, ?, ?)")) {
                ps.setString(1, idAdherent);
                ps.setObject(2, entree);
                ps.setBoolean(3, delaiGrace);
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                if ("23505".equals(e.getSQLState())) {   // violation d'unicité : déjà dans le club
                    return false;
                }
                throw e;
            }
        }

        /** ÉCRITURE : ferme la session ouverte. Retourne false s'il n'y en avait pas. */
        boolean cloturerPresence(String idAdherent, LocalDateTime sortie) throws SQLException {
            try (PreparedStatement ps = preparer(
                    "UPDATE club.presence SET date_heure_sortie = ? "
                  + "WHERE id_adherent = ? AND date_heure_sortie IS NULL AND date_heure_entree < ?")) {
                ps.setObject(1, sortie);
                ps.setString(2, idAdherent);
                ps.setObject(3, sortie);
                return ps.executeUpdate() == 1;
            }
        }

        /** Étage : l'adhérent a-t-il une séance réservée sur le créneau en cours ? */
        boolean aReservationSurCreneau(String idAdherent, LocalDateTime maintenant) throws SQLException {
            try (PreparedStatement ps = preparer(
                    "SELECT 1 FROM club.reserver r "
                  + "JOIN club.seance s ON s.id_seance = r.id_seance "
                  + "WHERE r.id_adherent = ? AND r.statut IN ('confirmée', 'présente') "
                  + "AND (s.date_seance + s.heure_debut) - (? * INTERVAL '1 minute') <= CAST(? AS timestamp) "
                  + "AND CAST(? AS timestamp) <= (s.date_seance + s.heure_fin) "
                  + "LIMIT 1")) {
                ps.setString(1, idAdherent);
                ps.setInt(2, MINUTES_AVANT_SEANCE);
                ps.setObject(3, maintenant);
                ps.setObject(4, maintenant);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        }

        /**
         * Salle : séance réservée, sur le créneau en cours, DANS LA SALLE de cette borne.
         * (le lien borne -> salle se fait par le nom : emplacement = « Entrée » + nom de la salle)
         * Retourne l'id de la séance, ou null.
         */
        Integer reservationPourSalle(int idBorne, String idAdherent, LocalDateTime maintenant) throws SQLException {
            try (PreparedStatement ps = preparer(
                    "SELECT r.id_seance FROM club.reserver r "
                  + "JOIN club.seance s ON s.id_seance = r.id_seance "
                  + "JOIN club.salle sa ON sa.id_salle = s.id_salle "
                  + "JOIN club.borne b ON b.id_borne = ? AND b.emplacement = 'Entrée ' || sa.nom_salle "
                  + "WHERE r.id_adherent = ? AND r.statut IN ('confirmée', 'présente') "
                  + "AND (s.date_seance + s.heure_debut) - (? * INTERVAL '1 minute') <= CAST(? AS timestamp) "
                  + "AND CAST(? AS timestamp) <= (s.date_seance + s.heure_fin) "
                  + "ORDER BY s.date_seance, s.heure_debut LIMIT 1")) {
                ps.setInt(1, idBorne);
                ps.setString(2, idAdherent);
                ps.setInt(3, MINUTES_AVANT_SEANCE);
                ps.setObject(4, maintenant);
                ps.setObject(5, maintenant);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : null;
                }
            }
        }

        /** ÉCRITURE : la réservation passe de « confirmée » à « présente ». */
        void marquerPresent(int idSeance, String idAdherent) throws SQLException {
            try (PreparedStatement ps = preparer(
                    "UPDATE club.reserver SET statut = 'présente' "
                  + "WHERE id_seance = ? AND id_adherent = ? AND statut = 'confirmée'")) {
                ps.setInt(1, idSeance);
                ps.setString(2, idAdherent);
                ps.executeUpdate();
            }
        }

        @Override
        public void close() {
            try {
                if (cx != null) {
                    cx.close();
                }
            } catch (SQLException ignore) {
                // rien à faire : on ferme de toute façon
            }
        }
    }

    // ------------------------------------------------------------------
    // 5) Les règles métier (pures : pas de réseau, pas de SQL)
    // ------------------------------------------------------------------

    /** Quel est l'état des abonnements de l'adhérent à la date donnée ? */
    static EtatAbonnement evaluerAbonnement(List<InfoAbonnement> abonnements, LocalDate aujourdhui) {
        EtatAbonnement meilleur = EtatAbonnement.REFUSE;
        for (InfoAbonnement a : abonnements) {
            if (a.statut().equals("suspendu") || aujourdhui.isBefore(a.debut())) {
                continue;                                   // suspendu ou pas encore démarré
            }
            if (a.statut().equals("actif") && !aujourdhui.isAfter(a.fin())) {
                return EtatAbonnement.VALIDE;               // en cours de validité
            }
            if (aujourdhui.isAfter(a.fin())
                    && !aujourdhui.isAfter(a.fin().plusDays(a.delaiGraceJours()))) {
                meilleur = EtatAbonnement.DELAI_GRACE;      // expiré mais dans le délai de grâce
            }
        }
        return meilleur;
    }

    /** Badge non actif, adhérent banni ou pénalité en cours -> refus « statut_admin ». */
    static boolean refuseParStatutAdmin(InfoAdherent a, LocalDate aujourdhui) {
        if (!"Actif".equals(a.etatBadge())) {
            return true;
        }
        return switch (a.statutAdmin()) {
            case "banni"    -> true;
            case "pénalisé" -> a.finPenalite() == null || !aujourdhui.isAfter(a.finPenalite());
            default         -> false;                       // « actif »
        };
    }

    /** Certificat médical absent ou expiré. */
    static boolean certificatInvalide(InfoAdherent a, LocalDate aujourdhui) {
        return a.certificatExpire() == null || aujourdhui.isAfter(a.certificatExpire());
    }

    // ------------------------------------------------------------------
    // 6) Le traitement d'UNE connexion (une instance de Session par client)
    // ------------------------------------------------------------------
    static class Session implements Runnable {
        private final Socket socket;
        private final String ip;
        private final BaseDeDonnees bd = new BaseDeDonnees();
        private OutputStream sortie;
        private InputStream entree;

        // état de la conversation avec CE client
        private InfoBorne borne = null;                   // rempli par IDENTIFIER
        private String enAttenteDelaiGrace = null;        // id de l'adhérent à qui on a proposé le délai de grâce
        private String adherentCourant = null;            // adhérent dont l'accès vient d'être autorisé
        private int nbErreurs = 0;

        Session(Socket socket, String ip) {
            this.socket = socket;
            this.ip = ip;
        }

        @Override
        @SuppressWarnings("try")
        public void run() {
            String client = socket.getRemoteSocketAddress().toString();
            log("connexion de " + client);
            try (Socket s = socket; BaseDeDonnees base = bd) {
                entree = new BufferedInputStream(s.getInputStream());
                sortie = s.getOutputStream();

                int nbCommandes = 0;
                while (true) {
                    String ligne;
                    try {
                        ligne = lireLigne();
                    } catch (LigneTropLongueException e) {
                        log("ligne trop longue : client déconnecté");
                        repondre("ERREUR ligne_trop_longue");
                        viderEntree();
                        break;
                    }
                    if (ligne == null) {
                        break;                                  // le client a fermé la connexion
                    }
                    ligne = ligne.trim();
                    if (ligne.isEmpty()) {
                        continue;
                    }
                    if (++nbCommandes > MAX_COMMANDES) {
                        repondre("ERREUR trop_de_commandes");
                        break;
                    }
                    log("<- " + ligne);
                    boolean continuer;
                    try {
                        continuer = traiter(ligne);
                    } catch (SQLException e) {
                        log("erreur base de données : " + e.getMessage());
                        repondre("ERREUR serveur_indisponible");   // on ne révèle aucun détail au client
                        continuer = true;
                    }
                    if (!continuer || nbErreurs >= MAX_ERREURS) {
                        break;
                    }
                }
            } catch (SocketTimeoutException e) {
                log("délai dépassé : client déconnecté");
            } catch (IOException e) {
                log("erreur réseau : " + e.getMessage());
            } finally {
                AtomicInteger n = CONNEXIONS_PAR_IP.get(ip);
                if (n != null) {
                    n.decrementAndGet();
                }
            }
            log("connexion fermée (" + client + ")");
        }

        static class LigneTropLongueException extends IOException {
            private static final long serialVersionUID = 1L;
        }

        /**
         * Avant de fermer après une faute du client : on dit « j'ai fini de parler »
         * (la réponse part), puis on jette ce qu'il continue d'envoyer, pendant un temps limité.
         * Sans cela, la fermeture brutale peut effacer notre message d'erreur chez lui.
         */
        private void viderEntree() {
            try {
                socket.shutdownOutput();
                socket.setSoTimeout(300);
                byte[] poubelle = new byte[4096];
                int total = 0;
                while (total < 65536) {
                    int n = entree.read(poubelle);
                    if (n < 0) {
                        break;
                    }
                    total += n;
                }
            } catch (IOException ignore) {
                // peu importe : on ferme de toute façon
            }
        }

        /**
         * Lit UNE ligne, avec des protections :
         *  - jamais plus de TAILLE_MAX_LIGNE octets en mémoire,
         *  - délai d'inactivité (court tant que la borne n'est pas identifiée),
         *  - une fois la ligne commencée, elle doit être terminée en DELAI_LIGNE_MAX ms.
         * Retourne null si le client a fermé la connexion.
         */
        private String lireLigne() throws IOException {
            ByteArrayOutputStream tampon = new ByteArrayOutputStream();
            int delaiInactivite = (borne == null) ? DELAI_AVANT_IDENT : DELAI_INACTIVITE;
            boolean commence = false;
            long limite = 0;
            while (true) {
                int attente = delaiInactivite;
                if (commence) {
                    long reste = limite - System.currentTimeMillis();
                    if (reste <= 0) {
                        throw new SocketTimeoutException("ligne trop lente");
                    }
                    attente = (int) reste;
                }
                socket.setSoTimeout(attente);
                int c = entree.read();
                if (c == -1) {
                    return commence ? tampon.toString(StandardCharsets.UTF_8) : null;
                }
                if (c == '\n') {
                    return tampon.toString(StandardCharsets.UTF_8);
                }
                if (!commence) {
                    commence = true;
                    limite = System.currentTimeMillis() + DELAI_LIGNE_MAX;
                }
                if (c != '\r') {
                    if (tampon.size() >= TAILLE_MAX_LIGNE) {
                        throw new LigneTropLongueException();
                    }
                    tampon.write(c);
                }
            }
        }

        /** Envoie une ligne au client. */
        private void repondre(String message) throws IOException {
            log("-> " + message);
            if (message.startsWith("ERREUR")) {
                nbErreurs++;
            }
            sortie.write((message + "\n").getBytes(StandardCharsets.UTF_8));
            sortie.flush();
        }

        /**
         * Analyse une commande. Retourne false pour fermer la connexion.
         * Accepte les deux écritures : « ACCES K82mQ41R09ab » et « ACCES(K82mQ41R09ab) »
         * (le rapport utilise les deux : tableau et diagrammes).
         */
        private boolean traiter(String ligne) throws IOException, SQLException {
            java.util.regex.Matcher m = MOTIF_COMMANDE.matcher(ligne);
            if (!m.matches()) {
                repondre("ERREUR syntaxe");
                return true;
            }
            String commande = m.group(1).toUpperCase(Locale.ROOT);
            String reste = m.group(2).trim();
            if (reste.startsWith("(")) {
                if (!reste.endsWith(")")) {
                    repondre("ERREUR syntaxe");
                    return true;
                }
                reste = reste.substring(1, reste.length() - 1).trim();
            }
            String[] args = reste.isEmpty() ? new String[0] : reste.split("[\\s,]+");

            if (commande.equals("IDENTIFIER")) {
                return traiterIdentifier(args);
            }
            // Toute autre commande exige d'avoir été identifié d'abord
            if (borne == null) {
                repondre("ERREUR identification_requise");
                return true;
            }
            switch (commande) {
                case "ACCES":
                    return traiterAcces(args);
                case "DECISION_DELAI_GRACE":
                    return traiterDecisionDelaiGrace(args);
                case "ACCES_ETAGE":
                    return traiterAccesEtage(args);
                case "SORTIE":
                    return traiterSortie(args);
                case "ENTREE_SALLE":
                    return traiterEntreeSalle(args);
                default:
                    repondre("ERREUR commande_inconnue");
                    return true;
            }
        }

        // ---------------- IDENTIFIER idBorne ----------------
        private boolean traiterIdentifier(String[] args) throws IOException, SQLException {
            if (args.length != 1 || !MOTIF_ID_BORNE.matcher(args[0]).matches()) {
                repondre("ERREUR syntaxe");
                return true;
            }
            if (borne != null) {
                repondre("ERREUR sequence");               // déjà identifiée
                return true;
            }
            InfoBorne b = bd.chercherBorne(Integer.parseInt(args[0]));
            if (b == null) {
                repondre("BORNE_INCONNUE");
                return false;                              // plus rien à faire : on ferme
            }
            if (!b.active()) {
                repondre("BORNE_INACTIVE");
                return false;
            }
            borne = b;
            repondre("BORNE_OK");
            return true;
        }

        // ---------------- ACCES id_adherent (borne 1) ----------------
        private boolean traiterAcces(String[] args) throws IOException, SQLException {
            adherentCourant = null;                        // une nouvelle demande annule la précédente
            enAttenteDelaiGrace = null;
            if (!verifierType(TypeBorne.CONTROLE_ACCES) || !verifierIdAdherent(args, 1)) {
                return true;
            }
            String id = args[0];
            InfoAdherent a = bd.chercherAdherent(id);
            if (a == null) {
                repondre("ACCES_REFUSE motif=adherent_inconnu");
                return true;
            }
            LocalDate aujourdhui = maintenant().toLocalDate();

            // Les vérifications s'enchaînent dans l'ordre du rapport : abonnement, statut admin, certificat.
            EtatAbonnement etat = evaluerAbonnement(bd.chercherAbonnements(id), aujourdhui);
            if (etat == EtatAbonnement.REFUSE) {
                repondre("ACCES_REFUSE motif=abonnement");
                return true;
            }
            if (refuseParStatutAdmin(a, aujourdhui) || bd.aUneSessionOuverte(id)) {   // inclut la double entrée
                repondre("ACCES_REFUSE motif=statut_admin");
                return true;
            }
            if (certificatInvalide(a, aujourdhui)) {
                repondre("ACCES_REFUSE motif=certificat");
                return true;
            }
            if (etat == EtatAbonnement.DELAI_GRACE) {
                enAttenteDelaiGrace = id;                  // on attend la décision de l'adhérent
                repondre("DELAI_GRACE_PROPOSE");
                return true;
            }
            return autoriserEntree(id, false, "ACCES_AUTORISE");
        }

        // ---------------- DECISION_DELAI_GRACE oui|non ----------------
        private boolean traiterDecisionDelaiGrace(String[] args) throws IOException, SQLException {
            if (!verifierType(TypeBorne.CONTROLE_ACCES)) {
                return true;
            }
            if (enAttenteDelaiGrace == null) {
                repondre("ERREUR sequence");               // aucune proposition en cours
                return true;
            }
            if (args.length != 1 || !(args[0].equalsIgnoreCase("oui") || args[0].equalsIgnoreCase("non"))) {
                repondre("ERREUR syntaxe");
                return true;
            }
            String id = enAttenteDelaiGrace;
            enAttenteDelaiGrace = null;
            if (args[0].equalsIgnoreCase("oui")) {
                return autoriserEntree(id, true, "ACCES_AUTORISE_AVERTISSEMENT");
            }
            repondre("ACCES_REFUSE motif=abonnement");
            return true;
        }

        // ---------------- ACCES_ETAGE (borne 1) ----------------
        private boolean traiterAccesEtage(String[] args) throws IOException, SQLException {
            if (!verifierType(TypeBorne.CONTROLE_ACCES) || !verifierNbArguments(args, 0)) {
                return true;
            }
            if (adherentCourant == null) {
                repondre("ERREUR sequence");               // personne n'a été autorisé à entrer
                return true;
            }
            boolean reserve = bd.aReservationSurCreneau(adherentCourant, maintenant());
            repondre(reserve ? "ETAGE_AUTORISE" : "ETAGE_VERROUILLE");
            return true;
        }

        // ---------------- SORTIE id_adherent horodatage (borne 1) ----------------
        private boolean traiterSortie(String[] args) throws IOException, SQLException {
            if (!verifierType(TypeBorne.CONTROLE_ACCES) || !verifierIdAdherent(args, 2)) {
                return true;
            }
            String id = args[0];
            LocalDateTime sortieHeure;
            try {
                sortieHeure = LocalDateTime.parse(args[1]);   // format ISO : 2026-10-25T19:41:00
            } catch (DateTimeParseException e) {
                repondre("ERREUR horodatage_invalide");
                return true;
            }
            if (sortieHeure.getYear() < 2000 || sortieHeure.getYear() > 2100) {
                repondre("ERREUR horodatage_invalide");
                return true;
            }
            LocalDateTime heureEntree = bd.entreeSessionOuverte(id);
            if (heureEntree == null) {
                repondre("ERREUR aucune_session_ouverte");
                return true;
            }
            if (!sortieHeure.isAfter(heureEntree)) {
                repondre("ERREUR horodatage_invalide");    // la sortie doit être après l'entrée
                return true;
            }
            if (!bd.cloturerPresence(id, sortieHeure)) {
                repondre("ERREUR aucune_session_ouverte");
                return true;
            }
            adherentCourant = null;
            repondre("SORTIE_ENREGISTREE " + formaterDuree(Duration.between(heureEntree, sortieHeure)));
            return true;
        }

        // ---------------- ENTREE_SALLE id_adherent (borne 2) ----------------
        private boolean traiterEntreeSalle(String[] args) throws IOException, SQLException {
            if (!verifierType(TypeBorne.SALLE_SEANCE) || !verifierIdAdherent(args, 1)) {
                return true;
            }
            String id = args[0];
            Integer seance = bd.reservationPourSalle(borne.id(), id, maintenant());
            if (seance == null) {
                repondre("SALLE_REFUSEE");
                return true;
            }
            bd.marquerPresent(seance, id);
            repondre("SALLE_AUTORISEE");
            return true;
        }

        // ---------------- utilitaires ----------------
        private boolean autoriserEntree(String id, boolean delaiGrace, String reponse) throws IOException, SQLException {
            if (!bd.ouvrirPresence(id, maintenant(), delaiGrace)) {
                repondre("ACCES_REFUSE motif=statut_admin");   // double entrée détectée par la base
                return true;
            }
            adherentCourant = id;
            repondre(reponse);
            return true;
        }

        /** Vérifie que la borne identifiée a le droit d'utiliser cette commande. */
        private boolean verifierType(TypeBorne attendu) throws IOException {
            if (borne.type() != attendu) {
                repondre("ERREUR commande_non_autorisee");
                return false;
            }
            return true;
        }

        private boolean verifierNbArguments(String[] args, int attendu) throws IOException {
            if (args.length != attendu) {
                repondre("ERREUR syntaxe");
                return false;
            }
            return true;
        }

        /** Vérifie le nombre d'arguments et le format de l'id_adherent (args[0]). */
        private boolean verifierIdAdherent(String[] args, int nbAttendus) throws IOException {
            if (args.length != nbAttendus || !MOTIF_ID_ADHERENT.matcher(args[0]).matches()) {
                repondre("ERREUR syntaxe");
                return false;
            }
            return true;
        }

        private static String formaterDuree(Duration d) {
            long s = d.getSeconds();
            return String.format("%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
        }

        /** Journal : on nettoie ce que le client a écrit (pas de caractères de contrôle) et on le raccourcit. */
        private void log(String texte) {
            String propre = texte.replaceAll("[^\\x20-\\x7E\\u00A0-\\u00FF]", "?");
            if (propre.length() > 200) {
                propre = propre.substring(0, 200) + "...";
            }
            System.out.println("[" + Thread.currentThread().getName() + "] " + propre);
        }
    }

    // ------------------------------------------------------------------
    // 7) Le programme principal : accepter les clients
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

        // Vérifications au démarrage : on préfère échouer tout de suite, avec un message clair.
        if (Config.MOT_DE_PASSE == null) {
            System.err.println("Variable d'environnement CLUB_DB_PASSWORD manquante (mot de passe de "
                    + Config.UTILISATEUR + ").");
            System.exit(1);
        }
        try {
            maintenant();
        } catch (DateTimeParseException e) {
            System.err.println("Valeur invalide pour -Dclub.maintenant (format 2026-10-12T17:50:00).");
            System.exit(1);
        }
        try (BaseDeDonnees test = new BaseDeDonnees()) {
            test.chercherBorne(1);
            System.out.println("Connexion à PostgreSQL : OK");
        } catch (SQLException e) {
            System.err.println("Impossible de joindre PostgreSQL : " + e.getMessage());
            System.exit(1);
        }
        if (System.getProperty("club.maintenant") != null) {
            System.out.println("ATTENTION : heure figée à " + maintenant() + " (mode test)");
        }

        // Au plus MAX_CLIENTS clients servis en parallèle, FILE_ATTENTE en attente, les autres sont refusés.
        ThreadPoolExecutor pool = new ThreadPoolExecutor(MAX_CLIENTS, MAX_CLIENTS, 0L,
                TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(FILE_ATTENTE));

        try (ServerSocket serveur = new ServerSocket()) {
            serveur.setReuseAddress(true);
            serveur.bind(new InetSocketAddress(port));
            System.out.println("Serveur en écoute sur le port " + port + " (Ctrl+C pour arrêter)");

            while (true) {
                Socket client;
                try {
                    client = serveur.accept();                 // bloque jusqu'à une connexion
                } catch (IOException e) {
                    System.err.println("accept() a échoué : " + e.getMessage());
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    continue;
                }
                String ip = client.getInetAddress().getHostAddress();
                AtomicInteger nb = CONNEXIONS_PAR_IP.computeIfAbsent(ip, k -> new AtomicInteger());
                if (nb.incrementAndGet() > MAX_PAR_IP) {
                    nb.decrementAndGet();
                    refuser(client, "ERREUR trop_de_connexions");
                    continue;
                }
                try {
                    pool.execute(new Session(client, ip));     // un thread s'occupe de ce client
                } catch (RejectedExecutionException e) {
                    nb.decrementAndGet();
                    refuser(client, "ERREUR serveur_occupe");
                }
            }
        } finally {
            pool.shutdown();
        }
    }

    /** Refuse poliment une connexion (une ligne d'explication) puis la ferme. */
    static void refuser(Socket client, String message) {
        try (Socket s = client) {
            s.setSoTimeout(1000);
            s.getOutputStream().write((message + "\n").getBytes(StandardCharsets.UTF_8));
            s.getOutputStream().flush();
        } catch (IOException ignore) {
            // le client est déjà parti
        }
    }
}

#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Client réseau - Borne de type 1 : contrôle d'accès (Club de Sport)
 
Protocole applicatif (rapport, sections 7 et 8) transporté en TCP :
la borne envoie une commande, attend la réponse du serveur, puis envoie
la commande suivante.
 
Messages gérés (borne -> serveur)      Réponses possibles du serveur
-------------------------------------  ---------------------------------------------
IDENTIFIER idBorne                     BORNE_OK | BORNE_INCONNUE | BORNE_INACTIVE
ACCES id_adherent                      ACCES_AUTORISE | DELAI_GRACE_PROPOSE
                                       | ACCES_REFUSE motif
DECISION_DELAI_GRACE oui|non           ACCES_AUTORISE_AVERTISSEMENT
                                       | ACCES_REFUSE motif=abonnement
ACCES_ETAGE                            ETAGE_AUTORISE | ETAGE_VERROUILLE
SORTIE id_adherent horodatage          SORTIE_ENREGISTREE duree
 
Scénarios du rapport couverts :
1. Entrée nominale          2. Entrée avec délai de grâce
  3. Accès refusé             4. Sortie de l'adhérent
  5. Borne non reconnue (BORNE_INCONNUE / BORNE_INACTIVE)
 
Chaque scénario commence par IDENTIFIER. L'entrée et la sortie sont deux
échanges distincts (une connexion TCP chacun), comme dans les diagrammes.
 
Utilisation :
    python3 borne1_client.py                       # menu interactif
    python3 borne1_client.py --host 127.0.0.1 --port 9000 --borne 1
"""
 
import argparse
import os
import re
import socket
import sys
from datetime import datetime
 
# ---------------------------------------------------------------------------
# Paramètres de connexion
# L'adresse et le port du serveur ne figurent pas dans le rapport :
# valeurs par défaut à adapter (options --host / --port) pour correspondre
# au serveur Java.
# ---------------------------------------------------------------------------
HOST_PAR_DEFAUT = "127.0.0.1"
PORT_PAR_DEFAUT = 9000
ID_BORNE_PAR_DEFAUT = 1          # borne 1 = controle_acces dans le jeu de données
TIMEOUT_PAR_DEFAUT = 5.0         # secondes, pour ne jamais rester bloqué
FICHIER_PUCE_PAR_DEFAUT = "puce.txt"
 
TAILLE_MAX_LIGNE = 1024          # on refuse toute réponse démesurée
ENCODAGE = "utf-8"
FIN_DE_LIGNE = "\n"              # un message = une ligne terminée par \n
 
# id_adherent : CHAR(12) alphanumérique (dictionnaire de données)
MOTIF_ID_ADHERENT = re.compile(r"^[A-Za-z0-9]{12}$")
 
 
class ErreurProtocole(Exception):
    """Réponse inattendue, vide, trop longue ou connexion perdue."""
 
 
# ---------------------------------------------------------------------------
# Puce de l'adhérent (simulée par un petit fichier : 0 ou 1)
# Le rapport précise que la borne 1 « écrit acces_etage = 1 (ou 0) sur la
# puce » ; la borne 2 le relit. Le fichier sert de puce pour la simulation.
# ---------------------------------------------------------------------------
def ecrire_puce(chemin, valeur):
    with open(chemin, "w", encoding="ascii") as f:
        f.write(str(valeur))
    print(f"[puce] acces_etage = {valeur} écrit sur la puce ({chemin})")
 
 
# ---------------------------------------------------------------------------
# Couche réseau
# ---------------------------------------------------------------------------
class Connexion:
    """Connexion TCP vers le serveur applicatif, échange ligne par ligne."""
 
    def __init__(self, host, port, timeout):
        self.host = host
        self.port = port
        self.timeout = timeout
        self.sock = None
        self.flux = None
 
    def ouvrir(self):
        try:
            self.sock = socket.create_connection((self.host, self.port),
                                                 timeout=self.timeout)
        except (socket.timeout, ConnectionRefusedError, OSError) as e:
            raise ErreurProtocole(
                f"Impossible de joindre le serveur {self.host}:{self.port} ({e})")
        self.sock.settimeout(self.timeout)
        self.flux = self.sock.makefile("rb")
 
    def echanger(self, message):
        """Envoie une commande et renvoie la réponse (une ligne, sans \\n)."""
        try:
            self.sock.sendall((message + FIN_DE_LIGNE).encode(ENCODAGE))
            print(f"  borne  -> serveur : {message}")
            brut = self.flux.readline(TAILLE_MAX_LIGNE + 1)
        except socket.timeout:
            raise ErreurProtocole("Délai dépassé : le serveur ne répond pas.")
        except OSError as e:
            raise ErreurProtocole(f"Connexion perdue ({e}).")
 
        if not brut:
            raise ErreurProtocole("Le serveur a fermé la connexion.")
        if len(brut) > TAILLE_MAX_LIGNE or not brut.endswith(b"\n"):
            raise ErreurProtocole("Réponse invalide (trop longue ou incomplète).")
        try:
            reponse = brut.decode(ENCODAGE).strip()
        except UnicodeDecodeError:
            raise ErreurProtocole("Réponse invalide (encodage).")
        if not reponse:
            raise ErreurProtocole("Réponse vide.")
        print(f"  serveur -> borne  : {reponse}")
        return reponse
 
    def fermer(self):
        """Fermeture propre : arrêt de l'émission puis fermeture du socket."""
        if self.sock is None:
            return
        try:
            self.sock.shutdown(socket.SHUT_RDWR)
        except OSError:
            pass
        try:
            if self.flux:
                self.flux.close()
            self.sock.close()
        finally:
            self.sock = None
            self.flux = None
            print("  (connexion fermée)")
 
 
def decouper(reponse):
    """'ACCES_REFUSE motif=abonnement' -> ('ACCES_REFUSE', 'motif=abonnement')"""
    parties = reponse.split(" ", 1)
    return parties[0], (parties[1].strip() if len(parties) > 1 else "")
 
 
def nettoyer_motif(arg):
    return arg[len("motif="):] if arg.startswith("motif=") else arg
 
 
# ---------------------------------------------------------------------------
# Saisies utilisateur
# ---------------------------------------------------------------------------
def demander_oui_non(question):
    while True:
        try:
            rep = input(f"{question} (oui/non) : ").strip().lower()
        except EOFError:
            raise ErreurProtocole("Saisie interrompue.")
        if rep in ("oui", "non"):
            return rep
        print("Réponse attendue : oui ou non.")
 
 
def demander_id_adherent():
    while True:
        try:
            id_adherent = input("id_adherent (12 caractères alphanumériques) : ").strip()
        except EOFError:
            raise ErreurProtocole("Saisie interrompue.")
        if MOTIF_ID_ADHERENT.match(id_adherent):
            return id_adherent
        print("Identifiant invalide : 12 caractères alphanumériques attendus.")
 
 
# ---------------------------------------------------------------------------
# Étape commune : IDENTIFIER
# ---------------------------------------------------------------------------
def identifier(conn, id_borne):
    """Retourne True si la borne est acceptée (BORNE_OK)."""
    reponse = conn.echanger(f"IDENTIFIER {id_borne}")
    code, _ = decouper(reponse)
    if code == "BORNE_OK":
        return True
    if code == "BORNE_INCONNUE":
        print(">> Borne non reconnue : aucune opération possible.")
        return False
    if code == "BORNE_INACTIVE":
        print(">> Borne désactivée : aucune opération possible.")
        return False
    raise ErreurProtocole(f"Réponse inattendue à IDENTIFIER : {reponse}")
 
 
# ---------------------------------------------------------------------------
# Scénarios 1, 2 et 3 : entrée de l'adhérent
# ---------------------------------------------------------------------------
def scenario_entree(conn, id_borne, fichier_puce):
    if not identifier(conn, id_borne):          # scénario 5
        return
 
    id_adherent = demander_id_adherent()
    reponse = conn.echanger(f"ACCES {id_adherent}")
    code, arg = decouper(reponse)
 
    if code == "ACCES_AUTORISE":                # scénario 1
        print(">> Accès autorisé.")
 
    elif code == "DELAI_GRACE_PROPOSE":         # scénario 2
        print(">> Abonnement expiré mais encore dans le délai de grâce.")
        choix = demander_oui_non("Accepter le délai de grâce ?")
        reponse = conn.echanger(f"DECISION_DELAI_GRACE {choix}")
        code, arg = decouper(reponse)
        if code == "ACCES_AUTORISE_AVERTISSEMENT":
            print(">> Accès autorisé avec avertissement (délai de grâce utilisé).")
        elif code == "ACCES_REFUSE":
            print(f">> Accès refusé (motif : {nettoyer_motif(arg)}).")
            return
        else:
            raise ErreurProtocole(f"Réponse inattendue à DECISION_DELAI_GRACE : {reponse}")
 
    elif code == "ACCES_REFUSE":                # scénario 3
        print(f">> Accès refusé (motif : {nettoyer_motif(arg)}).")
        return
 
    else:
        raise ErreurProtocole(f"Réponse inattendue à ACCES : {reponse}")
 
    # --- Accès à l'étage -----------------------------------------------
    if demander_oui_non("Séance collective réservée ?") == "non":
        # Aucun échange réseau : la borne écrit acces_etage = 0 sur la puce.
        ecrire_puce(fichier_puce, 0)
        return
 
    reponse = conn.echanger("ACCES_ETAGE")
    code, _ = decouper(reponse)
    if code == "ETAGE_AUTORISE":
        print(">> Étage autorisé.")
        ecrire_puce(fichier_puce, 1)
    elif code == "ETAGE_VERROUILLE":
        print(">> Étage verrouillé (aucune réservation sur ce créneau).")
        ecrire_puce(fichier_puce, 0)
    else:
        raise ErreurProtocole(f"Réponse inattendue à ACCES_ETAGE : {reponse}")
 
 
# ---------------------------------------------------------------------------
# Scénario 4 : sortie de l'adhérent
# ---------------------------------------------------------------------------
def scenario_sortie(conn, id_borne):
    if not identifier(conn, id_borne):
        return
 
    id_adherent = demander_id_adherent()
    # Horodatage ISO 8601 sans espace, pour ne pas casser le découpage par espaces.
    horodatage = datetime.now().strftime("%Y-%m-%dT%H:%M:%S")
    reponse = conn.echanger(f"SORTIE {id_adherent} {horodatage}")
    code, arg = decouper(reponse)
    if code == "SORTIE_ENREGISTREE":
        print(f">> Sortie enregistrée. Durée de présence : {arg or 'non précisée'}.")
    else:
        raise ErreurProtocole(f"Réponse inattendue à SORTIE : {reponse}")
 
 
# ---------------------------------------------------------------------------
# Programme principal
# ---------------------------------------------------------------------------
def lancer(scenario, args):
    conn = Connexion(args.host, args.port, args.timeout)
    try:
        conn.ouvrir()
        scenario(conn)
    except ErreurProtocole as e:
        print(f"!! Erreur : {e}")
    except KeyboardInterrupt:
        print("\n!! Interrompu par l'utilisateur.")
    finally:
        conn.fermer()
 
 
def main():
    parser = argparse.ArgumentParser(description="Client borne 1 (contrôle d'accès)")
    parser.add_argument("--host", default=HOST_PAR_DEFAUT)
    parser.add_argument("--port", type=int, default=PORT_PAR_DEFAUT)
    parser.add_argument("--borne", type=int, default=ID_BORNE_PAR_DEFAUT,
                        help="id_borne (entier > 0)")
    parser.add_argument("--timeout", type=float, default=TIMEOUT_PAR_DEFAUT)
    parser.add_argument("--puce", default=FICHIER_PUCE_PAR_DEFAUT,
                        help="fichier simulant la puce de l'adhérent")
    args = parser.parse_args()
 
    if args.borne <= 0:
        parser.error("id_borne doit être un entier > 0")
 
    puce = os.path.abspath(args.puce)
    while True:
        print("\n=== Borne 1 : contrôle d'accès ===")
        print("1) Entrée d'un adhérent")
        print("2) Sortie d'un adhérent")
        print("3) Quitter")
        try:
            choix = input("Choix : ").strip()
        except (EOFError, KeyboardInterrupt):
            print()
            break
        if choix == "1":
            lancer(lambda c: scenario_entree(c, args.borne, puce), args)
        elif choix == "2":
            lancer(lambda c: scenario_sortie(c, args.borne), args)
        elif choix == "3":
            break
        else:
            print("Choix invalide.")
    return 0

 
if __name__ == "__main__":
    sys.exit(main())




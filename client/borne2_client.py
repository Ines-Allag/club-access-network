#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Client réseau - Borne de type 2 : salle de séance (Club de Sport)
 
Protocole applicatif (rapport, sections 7 et 8) transporté en TCP :
la borne envoie une commande, attend la réponse du serveur, puis envoie
la commande suivante.
 
Messages gérés (borne -> serveur)      Réponses possibles du serveur
-------------------------------------  ---------------------------------------------
IDENTIFIER idBorne                     BORNE_OK | BORNE_INCONNUE | BORNE_INACTIVE
ENTREE_SALLE id_adherent               SALLE_AUTORISEE | SALLE_REFUSEE
 
Scénarios du rapport couverts :
  1. Puce à 0 : porte fermée (aucune demande envoyée après IDENTIFIER)
  2. Puce à 1 : entrée autorisée (SALLE_AUTORISEE)
  3. Puce à 1 : aucune réservation (SALLE_REFUSEE)
  + borne non reconnue / désactivée (BORNE_INCONNUE / BORNE_INACTIVE)
 
La puce de l'adhérent (valeur acces_etage écrite par la borne 1) est
simulée par un petit fichier texte contenant 0 ou 1 (par défaut puce.txt,
le même fichier que celui écrit par borne1_client.py).
 
Utilisation :
    python3 borne2_client.py
    python3 borne2_client.py --host 127.0.0.1 --port 9000 --borne 2
"""
 
import argparse
import os
import re
import socket
import sys
 
# ---------------------------------------------------------------------------
# Paramètres de connexion
# L'adresse et le port du serveur ne figurent pas dans le rapport :
# valeurs par défaut à adapter (options --host / --port) pour correspondre
# au serveur Java.
# ---------------------------------------------------------------------------
HOST_PAR_DEFAUT = "127.0.0.1"
PORT_PAR_DEFAUT = 9000
ID_BORNE_PAR_DEFAUT = 2          # borne 2 = salle_seance dans le jeu de données
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
# Puce de l'adhérent (simulée par un fichier : 0 ou 1)
# ---------------------------------------------------------------------------
def lire_puce(chemin):
    """Retourne acces_etage (0 ou 1). Fichier absent ou invalide -> 0 (porte fermée)."""
    try:
        with open(chemin, "r", encoding="ascii") as f:
            valeur = f.read().strip()
    except (OSError, UnicodeDecodeError):
        print(f"[puce] fichier {chemin} illisible : on considère acces_etage = 0")
        return 0
    if valeur == "1":
        return 1
    if valeur == "0":
        return 0
    print(f"[puce] valeur invalide ({valeur!r}) : on considère acces_etage = 0")
    return 0
 
 
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
    """'SALLE_REFUSEE' -> ('SALLE_REFUSEE', '')"""
    parties = reponse.split(" ", 1)
    return parties[0], (parties[1].strip() if len(parties) > 1 else "")
 
 
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
# Scénarios 1, 2 et 3 : présentation d'un adhérent devant la porte de la salle
# ---------------------------------------------------------------------------
def scenario_salle(conn, id_borne, fichier_puce):
    # IDENTIFIER (toujours en premier)
    reponse = conn.echanger(f"IDENTIFIER {id_borne}")
    code, _ = decouper(reponse)
    if code == "BORNE_INCONNUE":
        print(">> Borne non reconnue : aucune opération possible.")
        return
    if code == "BORNE_INACTIVE":
        print(">> Borne désactivée : aucune opération possible.")
        return
    if code != "BORNE_OK":
        raise ErreurProtocole(f"Réponse inattendue à IDENTIFIER : {reponse}")
 
    # Lecture de la puce : acces_etage
    acces_etage = lire_puce(fichier_puce)
    print(f"[puce] acces_etage = {acces_etage}")
 
    if acces_etage == 0:                        # scénario 1
        print(">> Puce à 0 : porte de la salle fermée. Aucune demande envoyée au serveur.")
        return
 
    # Puce à 1 : demande d'entrée dans la salle
    id_adherent = demander_id_adherent()
    reponse = conn.echanger(f"ENTREE_SALLE {id_adherent}")
    code, _ = decouper(reponse)
    if code == "SALLE_AUTORISEE":               # scénario 2
        print(">> Entrée autorisée : la porte de la salle s'ouvre. Présence enregistrée.")
    elif code == "SALLE_REFUSEE":               # scénario 3
        print(">> Entrée refusée : la porte de la salle reste fermée.")
    else:
        raise ErreurProtocole(f"Réponse inattendue à ENTREE_SALLE : {reponse}")
 
 
# ---------------------------------------------------------------------------
# Programme principal
# ---------------------------------------------------------------------------
def lancer(args, puce):
    conn = Connexion(args.host, args.port, args.timeout)
    try:
        conn.ouvrir()
        scenario_salle(conn, args.borne, puce)
    except ErreurProtocole as e:
        print(f"!! Erreur : {e}")
    except KeyboardInterrupt:
        print("\n!! Interrompu par l'utilisateur.")
    finally:
        conn.fermer()
 
 
def main():
    parser = argparse.ArgumentParser(description="Client borne 2 (salle de séance)")
    parser.add_argument("--host", default=HOST_PAR_DEFAUT)
    parser.add_argument("--port", type=int, default=PORT_PAR_DEFAUT)
    parser.add_argument("--borne", type=int, default=ID_BORNE_PAR_DEFAUT,
                        help="id_borne (entier > 0)")
    parser.add_argument("--timeout", type=float, default=TIMEOUT_PAR_DEFAUT)
    parser.add_argument("--puce", default=FICHIER_PUCE_PAR_DEFAUT,
                        help="fichier simulant la puce de l'adhérent (0 ou 1)")
    args = parser.parse_args()
 
    if args.borne <= 0:
        parser.error("id_borne doit être un entier > 0")
 
    puce = os.path.abspath(args.puce)
    while True:
        print("\n=== Borne 2 : salle de séance ===")
        print("1) Présenter un adhérent devant la porte")
        print("2) Quitter")
        try:
            choix = input("Choix : ").strip()
        except (EOFError, KeyboardInterrupt):
            print()
            break
        if choix == "1":
            lancer(args, puce)
        elif choix == "2":
            break
        else:
            print("Choix invalide.")
    return 0
 
 
if __name__ == "__main__":
    sys.exit(main())

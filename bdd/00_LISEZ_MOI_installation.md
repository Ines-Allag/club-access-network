# Mettre en place l'environnement (base + réseau)

Objectif : avoir sur ton PC une base PostgreSQL `clubsport` remplie avec le jeu de données
du rapport, plus Java, Python et Netcat pour tester la partie réseau.

Les 3 scripts de ce dossier ont été **testés sur PostgreSQL 16** (création + chargement + recalage des compteurs).
Ils corrigent 2 erreurs du rapport (voir fin de ce fichier).

---

## 1. Vérifier ce qui est déjà installé

Ouvre un terminal (Windows : « Invite de commandes » ou PowerShell ; Mac/Linux : Terminal) et tape :

```
psql --version
java -version
javac -version
python --version        (ou python3 --version sur Mac/Linux)
nc -h                   (Mac/Linux)      ncat --version   (Windows, après installation de Nmap)
```

Une commande « introuvable » = ce programme n'est pas installé (ou pas dans le PATH).

## 2. Installer ce qui manque

| Outil | Windows | Mac | Linux (Ubuntu/Debian) |
|---|---|---|---|
| PostgreSQL 16 | installeur EDB sur postgresql.org/download/windows (garde le port 5432, retiens le mot de passe de l'utilisateur `postgres`) | `brew install postgresql@16` | `sudo apt install postgresql postgresql-client` |
| JDK (Java) 21 | Temurin 21 sur adoptium.net | `brew install --cask temurin@21` | `sudo apt install default-jdk` |
| Python 3 | python.org (coche « Add to PATH ») | `brew install python` | déjà présent en général |
| Netcat | installer **Nmap** (nmap.org) : il fournit `ncat`, équivalent de `nc` | déjà présent (`nc`) | `sudo apt install netcat-openbsd` |
| Pilote JDBC | télécharger `postgresql-xx.jar` sur jdbc.postgresql.org (il servira au serveur Java) | idem | idem |

Sous Windows, si `psql` est « introuvable », ajoute `C:\Program Files\PostgreSQL\16\bin` au PATH.

## 3. Créer la base et la remplir

Place-toi dans le dossier qui contient les 3 fichiers `.sql`, puis :

```
psql -U postgres -c "CREATE DATABASE clubsport ENCODING 'UTF8' TEMPLATE template0;"
psql -U postgres -d clubsport -v ON_ERROR_STOP=1 -f 01_schema.sql
psql -U postgres -d clubsport -v ON_ERROR_STOP=1 -c "SET search_path TO club" -f 02_donnees.sql
psql -U postgres -d clubsport -v ON_ERROR_STOP=1 -f 03_sequences.sql
```

(Mot de passe demandé : celui choisi à l'installation. Sous Linux, si la connexion est refusée :
`sudo -u postgres psql ...`.)

> Attention Windows : si les accents s'affichent mal, tape `chcp 65001` avant, et ajoute
> `-c "SET client_encoding TO 'UTF8'"` si besoin.

## 4. Vérifier que ça a marché

```
psql -U postgres -d clubsport -c "SELECT count(*) FROM club.adherent;"      -> 10
psql -U postgres -d clubsport -c "SELECT count(*) FROM club.borne;"         -> 6
psql -U postgres -d clubsport -c "SELECT id_adherent, nom FROM club.adherent;"
```

Résultats attendus au total : adherent 10, abonnement 10, borne 6, presence 8, seance 6.

Pour tout recommencer à zéro :
```
psql -U postgres -c "DROP DATABASE clubsport;"
```
puis refais l'étape 3.

## 5. Créer un utilisateur dédié au serveur (à faire plus tard, pour la sécurité)

Le serveur Java ne doit pas se connecter en `postgres` (super-utilisateur). On lui donnera un
compte limité aux seules tables nécessaires. On le fera ensemble au moment de brancher JDBC.

---

## Ce que ces scripts corrigent par rapport au rapport (Rapport_v4.docx)

1. **Doublon dans le script INSERT** : le bloc « SALLE » apparaît deux fois (avec son titre).
   Sans correction : `duplicate key value violates unique constraint "salle_pkey"`.
2. **Espace parasite** dans `'y.haddad @etu.cyu.fr'` (adhérent_etudiant). Sans correction,
   la contrainte CHECK de l'e-mail refuse la ligne.
3. **Compteurs automatiques (identity)** : les INSERT imposent les id avec
   `OVERRIDING SYSTEM VALUE`, donc le compteur reste à 1. Sans `03_sequences.sql`,
   le premier INSERT « normal » d'une présence échoue avec une clé dupliquée.
   (C'est exactement ce que fera le serveur lors d'un ACCES autorisé.)

Pense à corriger le rapport en conséquence.

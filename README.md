# Projet SAÉ BD & Réseau — Le Club de Sport

| Dossier  | Contenu |
|----------|---------|
| bdd/     | Scripts PostgreSQL : 01_schema.sql, 02_donnees.sql, 03_sequences.sql (à lancer dans cet ordre) |
| serveur/ | Serveur applicatif Java. src/ = code, lib/ = pilote JDBC (.jar), out/ = fichiers compilés |
| client/  | Clients Python des bornes (borne1_client.py, borne2_client.py) |
| tests/   | Tests réseau (scénarios Netcat, scripts de robustesse) et leurs résultats |
| docs/    | Rapport, sujet de la SAÉ, diagrammes |
| notes/   | Tes notes d'apprentissage (ce que tu as compris, bugs rencontrés) |

Lancer le serveur (depuis le dossier serveur/) :
    javac -d out src\Serveur.java
    java -cp out Serveur

Lancer un client (depuis le dossier client/) :
    python borne1_client.py

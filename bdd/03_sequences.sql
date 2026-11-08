-- À lancer APRÈS 02_donnees.sql.
-- Les INSERT du jeu de données imposent les id (OVERRIDING SYSTEM VALUE) :
-- le compteur automatique reste à 1 et le prochain INSERT « normal »
-- (ex. création d'une présence par le serveur) échouerait (clé dupliquée).
-- Ce script recale chaque compteur sur le plus grand id existant.
SET search_path TO club;
DO $$
DECLARE r record;
BEGIN
  FOR r IN
    SELECT c.table_name, c.column_name,
           pg_get_serial_sequence(format('club.%I', c.table_name), c.column_name) AS seq
    FROM information_schema.columns c
    WHERE c.table_schema = 'club' AND c.is_identity = 'YES'
  LOOP
    EXECUTE format('SELECT setval(%L, COALESCE((SELECT MAX(%I) FROM club.%I), 0) + 1, false)',
                   r.seq, r.column_name, r.table_name);
  END LOOP;
END $$;

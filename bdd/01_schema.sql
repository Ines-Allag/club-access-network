CREATE SCHEMA club;
SET search_path TO club;
CREATE TABLE personnel (
    id_pers        INTEGER      PRIMARY KEY
                   CHECK (id_pers > 10000000 AND id_pers < 20000000),
    nom            VARCHAR(30)  NOT NULL CHECK (nom    ~ '^[A-Za-zÀ-ÿ'' -]+$'),
    prenom         VARCHAR(30)  NOT NULL CHECK (prenom ~ '^[A-Za-zÀ-ÿ'' -]+$'),
    telephone      CHAR(10)     NOT NULL CHECK (telephone ~ '^[0-9]{10}$'),
    email          VARCHAR(320) NOT NULL UNIQUE
                   CHECK (email ~ '^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$'),
    date_embauche  DATE         NOT NULL CHECK (date_embauche > DATE '1900-01-01'),
    photo          BYTEA        CHECK (photo IS NULL OR octet_length(photo) <= 102400)
);

CREATE TABLE receptionniste (
    id_pers   INTEGER  PRIMARY KEY
              REFERENCES personnel(id_pers) ON DELETE CASCADE ON UPDATE CASCADE,
    num_poste INTEGER  NOT NULL CHECK (num_poste > 0)
);

CREATE TABLE coach (
    id_pers           INTEGER      PRIMARY KEY
                      REFERENCES personnel(id_pers) ON DELETE CASCADE ON UPDATE CASCADE,
    specialite        VARCHAR(30)  NOT NULL,
    date_certif       DATE         NOT NULL CHECK (date_certif > DATE '1900-01-01'),
    niveau_experience VARCHAR(30)  NOT NULL
                      CHECK (niveau_experience IN ('débutant','intermédiaire','confirmé','expert'))
);

CREATE TABLE salle (
    id_salle   INTEGER       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nom_salle  VARCHAR(30)   NOT NULL UNIQUE CHECK (length(trim(nom_salle)) > 0),
    type_salle VARCHAR(30)   NOT NULL,
    capacite   INTEGER       NOT NULL CHECK (capacite > 0),
    superficie NUMERIC(7,2)  NOT NULL CHECK (superficie > 0)
);

CREATE TABLE cours (
    id_cours     INTEGER      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nom          VARCHAR(50)  NOT NULL UNIQUE CHECK (length(trim(nom)) > 0),
    jour_semaine VARCHAR(16)  NOT NULL CHECK (jour_semaine IN ('Lundi', 'Mardi', 'Mercredi', 'Jeudi', 'Vendredi', 'Samedi', 'Dimanche')),
    niveau       VARCHAR(15)  NOT NULL CHECK (niveau IN ('débutant','intermédiaire','avancé')),
    capacite_max INTEGER      NOT NULL CHECK (capacite_max > 0)
);
CREATE TABLE entreprise_partenaire (
    id_entreprise        CHAR(9)      PRIMARY KEY CHECK (id_entreprise ~ '^[A-Za-z0-9]{9}$'),
    nom                  VARCHAR(30)  NOT NULL,
    siret                CHAR(14)     NOT NULL UNIQUE CHECK (siret ~ '^[0-9]{14}$'),
    numero_adresse       VARCHAR(5)   NOT NULL CHECK (numero_adresse ~ '^[0-9]{1,5}$'),
    adresse              VARCHAR(45)  NOT NULL,
    code_postal          CHAR(5)      NOT NULL CHECK (code_postal ~ '^[0-9]{5}$'),
    ville                VARCHAR(45)  NOT NULL,
    email_contact        VARCHAR(320) NOT NULL UNIQUE
                         CHECK (email_contact ~ '^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$'),
    telephone_contact    CHAR(10)     NOT NULL UNIQUE CHECK (telephone_contact ~ '^[0-9]{10}$'),
    debut_partenariat    DATE         NOT NULL,
    echeance_partenariat DATE         NOT NULL,
    CHECK (echeance_partenariat > debut_partenariat)
);

CREATE TABLE adherent (
    id_adherent           CHAR(12)     PRIMARY KEY CHECK (id_adherent ~ '^[A-Za-z0-9]{12}$'),
    nom                   VARCHAR(30)  NOT NULL CHECK (nom    ~ '^[A-Za-zÀ-ÿ'' -]+$'),
    prenom                VARCHAR(30)  NOT NULL CHECK (prenom ~ '^[A-Za-zÀ-ÿ'' -]+$'),
    genre                 CHAR(1)      NOT NULL CHECK (genre IN ('F','M')),
    date_naissance        DATE         NOT NULL CHECK (date_naissance > DATE '1900-01-01'),
    email                 VARCHAR(320) NOT NULL UNIQUE
                          CHECK (email ~ '^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$'),
    telephone             CHAR(10)     NOT NULL UNIQUE CHECK (telephone ~ '^[0-9]{10}$'),
    canal_contact_favoris VARCHAR(9)   NOT NULL CHECK (canal_contact_favoris IN ('email','telephone')),
    code_postal           CHAR(5)      NOT NULL CHECK (code_postal ~ '^[0-9]{5}$'),
    ville                 VARCHAR(45)  NOT NULL,
    certif_medical_exp    DATE,
    photo                 BYTEA        CHECK (photo IS NULL OR octet_length(photo) <= 102400),
    etat_badge            VARCHAR(10)  NOT NULL CHECK (etat_badge IN ('Actif','Inactif','Volé')),
    statut_admin          VARCHAR(10)  NOT NULL CHECK (statut_admin IN ('actif', 'banni', 'pénalisé')),
    fin_penalite          DATE
);

CREATE TABLE adherent_etudiant (
    id_adherent           CHAR(12)     PRIMARY KEY
                          REFERENCES adherent(id_adherent) ON DELETE CASCADE ON UPDATE CASCADE,
    mail_etudiant         VARCHAR(320) NOT NULL UNIQUE
                          CHECK (mail_etudiant ~ '^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$'),
    etablissement         VARCHAR(100) NOT NULL,
    type_formation        VARCHAR(50)  NOT NULL,
    date_debut_statut_etu DATE         NOT NULL CHECK (date_debut_statut_etu > DATE '2000-01-01'),
    date_fin_statut_etu   DATE         NOT NULL,
    CHECK (date_fin_statut_etu >= date_debut_statut_etu + 365)
);

CREATE TABLE adherent_entreprise (
    id_adherent            CHAR(12)    PRIMARY KEY
                           REFERENCES adherent(id_adherent) ON DELETE CASCADE ON UPDATE CASCADE,
    type_contrat           VARCHAR(10) NOT NULL,
    expiration_eligibilite DATE,
    id_entreprise          CHAR(9)     NOT NULL
                           REFERENCES entreprise_partenaire(id_entreprise) ON UPDATE CASCADE
);

CREATE TABLE abonnement (
    id_abonnement       INTEGER     GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    type_abonnement     VARCHAR(12) NOT NULL CHECK (type_abonnement IN ('mensuel','trimestriel','annuel')),
    date_debut          DATE        NOT NULL,
    date_fin            DATE        NOT NULL,
    statut              VARCHAR(10) NOT NULL CHECK (statut IN ('actif','expiré','suspendu')),
    delai_grace_jours   INTEGER     NOT NULL CHECK (delai_grace_jours > 0),
    id_adherent         CHAR(12)    NOT NULL REFERENCES adherent(id_adherent)
                                    ON UPDATE CASCADE ON DELETE CASCADE,
    CHECK (date_fin > date_debut)
);

CREATE TABLE paiement (
    id_paiement   INTEGER      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    date_paiement DATE         NOT NULL,
    montant       NUMERIC(6,2) NOT NULL CHECK (montant > 0),
    mode_paiement VARCHAR(10)  NOT NULL CHECK (mode_paiement IN ('carte','espèces','virement','chèque')),
    id_abonnement INTEGER      NOT NULL
                  REFERENCES abonnement(id_abonnement) ON UPDATE CASCADE
);
CREATE TABLE seance (
    id_seance               INTEGER  GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    date_seance             DATE     NOT NULL,
    heure_debut             TIME(0)  NOT NULL,
    heure_fin               TIME(0)  NOT NULL,
    id_salle                INTEGER  NOT NULL REFERENCES salle(id_salle) ON UPDATE CASCADE ON DELETE CASCADE,
    id_coach                INTEGER  NOT NULL REFERENCES coach(id_pers) ON UPDATE CASCADE,
    id_receptionniste       INTEGER  NOT NULL REFERENCES receptionniste(id_pers)
                                     ON UPDATE CASCADE ON DELETE CASCADE,
    CHECK (heure_fin > heure_debut)
);

CREATE TABLE programmer (
    id_seance INTEGER PRIMARY KEY REFERENCES seance(id_seance) ON DELETE CASCADE ON UPDATE CASCADE,
    id_cours  INTEGER NOT NULL    REFERENCES cours(id_cours)   ON UPDATE CASCADE
);

CREATE TABLE qualifier (
    id_pers_coach INTEGER NOT NULL REFERENCES coach(id_pers) ON DELETE CASCADE ON UPDATE CASCADE,
    id_cours      INTEGER NOT NULL REFERENCES cours(id_cours) ON DELETE CASCADE ON UPDATE CASCADE,
    date_obtention DATE   NOT NULL,
    PRIMARY KEY (id_pers_coach, id_cours)
);

CREATE TABLE reserver (
    id_seance        INTEGER     NOT NULL REFERENCES seance(id_seance) ON DELETE CASCADE ON UPDATE CASCADE,
    id_adherent      CHAR(12)    NOT NULL REFERENCES adherent(id_adherent) ON DELETE CASCADE ON UPDATE CASCADE,
    date_reservation TIMESTAMP   NOT NULL DEFAULT now(),
    statut           VARCHAR(10) NOT NULL DEFAULT 'confirmée'
                     CHECK (statut IN ('confirmée','annulée','en_attente')),
    PRIMARY KEY (id_seance, id_adherent)
);

CREATE TABLE presence (
    id_presence         INTEGER      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_adherent         CHAR(12)     NOT NULL REFERENCES adherent(id_adherent) ON DELETE CASCADE ON UPDATE CASCADE,
    date_heure_entree   TIMESTAMP(0) NOT NULL,
    date_heure_sortie   TIMESTAMP(0),
    delai_grace_utilise BOOLEAN      NOT NULL,
    CHECK (date_heure_sortie > date_heure_entree)
);

CREATE TABLE borne (
    id_borne    INTEGER     GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    type_borne  VARCHAR(30) NOT NULL CHECK (type_borne IN ('controle_acces', 'salle_seance')),
    statut      VARCHAR(30) NOT NULL CHECK (statut IN ('active', 'désactivée')),
    emplacement VARCHAR(50) NOT NULL
);
-- ---------------------------------------------------------------------
-- Indexes
-- ---------------------------------------------------------------------
CREATE INDEX idx_abonnement_adherent ON abonnement(id_adherent);
CREATE INDEX idx_paiement_abonnement ON paiement(id_abonnement);
CREATE INDEX idx_seance_salle        ON seance(id_salle);
CREATE INDEX idx_seance_coach        ON seance(id_coach);
CREATE INDEX idx_seance_date         ON seance(date_seance);
CREATE INDEX idx_presence_adherent   ON presence(id_adherent, date_heure_entree);
CREATE INDEX idx_adh_entreprise_ent  ON adherent_entreprise(id_entreprise);

-- ---------------------------------------------------------------------
-- Exclusivity Triggers
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION check_exclusive() RETURNS trigger AS $$
DECLARE
    clash boolean;
BEGIN
    EXECUTE format('SELECT EXISTS (SELECT 1 FROM %I WHERE %I::text = $1)', TG_ARGV[0], TG_ARGV[1])
       INTO clash USING (to_jsonb(NEW) ->> TG_ARGV[1]);
    IF clash THEN
        RAISE EXCEPTION 'Exclusive specialisation violated: % already exists in %', NEW, TG_ARGV[0];
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_excl_coach
    BEFORE INSERT OR UPDATE ON coach
    FOR EACH ROW EXECUTE FUNCTION check_exclusive('receptionniste', 'id_pers');

CREATE TRIGGER trg_excl_receptionniste
    BEFORE INSERT OR UPDATE ON receptionniste
    FOR EACH ROW EXECUTE FUNCTION check_exclusive('coach', 'id_pers');

CREATE TRIGGER trg_excl_etudiant
    BEFORE INSERT OR UPDATE ON adherent_etudiant
    FOR EACH ROW EXECUTE FUNCTION check_exclusive('adherent_entreprise', 'id_adherent');

CREATE TRIGGER trg_excl_entreprise
    BEFORE INSERT OR UPDATE ON adherent_entreprise
    FOR EACH ROW EXECUTE FUNCTION check_exclusive('adherent_etudiant', 'id_adherent');

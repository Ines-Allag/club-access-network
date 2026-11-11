CREATE SCHEMA IF NOT EXISTS club;
SET search_path TO club;

-- ---------------------------------------------------------------------
-- Types Enumérés (ENUMs) / Optionnelle mais rends les tables un peu plus lisible
-- ---------------------------------------------------------------------
CREATE TYPE genre_enum AS ENUM ('F', 'M');
CREATE TYPE canal_contact_enum AS ENUM ('email', 'telephone');
CREATE TYPE etat_badge_enum AS ENUM ('Actif', 'Inactif', 'Volé');
CREATE TYPE statut_admin_enum AS ENUM ('actif', 'banni', 'pénalisé');
CREATE TYPE type_contrat_enum AS ENUM ('CDD', 'CDI', 'Alternance', 'Stage');
CREATE TYPE type_abonnement_enum AS ENUM ('mensuel', 'trimestriel', 'annuel');
CREATE TYPE statut_abonnement_enum AS ENUM ('non démarré', 'actif', 'expiré', 'suspendu');
CREATE TYPE mode_paiement_enum AS ENUM ('carte', 'espèces', 'virement', 'chèque');
CREATE TYPE specialite_coach_enum AS ENUM ('Musculation', 'Cardio', 'Fitness', 'Yoga', 'Pilates', 'Cross-training', 'Aquagym');
CREATE TYPE niveau_experience_enum AS ENUM ('débutant', 'intermédiaire', 'confirmé', 'expert');
CREATE TYPE type_salle_enum AS ENUM ('Cardio-training', 'Musculation', 'Cours collectif', 'Bien-être', 'Polyvalente');
CREATE TYPE niveau_cours_enum AS ENUM ('débutant', 'intermédiaire', 'avancé');
CREATE TYPE jour_semaine_enum AS ENUM ('Lundi', 'Mardi', 'Mercredi', 'Jeudi', 'Vendredi', 'Samedi', 'Dimanche');
CREATE TYPE statut_reservation_enum AS ENUM ('confirmée', 'annulée', 'en_attente');
CREATE TYPE type_borne_enum AS ENUM ('controle_acces', 'salle_seance');
CREATE TYPE statut_borne_enum AS ENUM ('active', 'désactivée');

-- ---------------------------------------------------------------------
-- Tables
-- ---------------------------------------------------------------------
CREATE TABLE personnel (
    id_pers        INTEGER      PRIMARY KEY
                   CHECK (id_pers > 10000000 AND id_pers < 20000000),
    nom            VARCHAR(30)  NOT NULL CHECK (nom ~ '^[A-Za-zÀ-ÿ'' -]+$' AND length(trim(nom)) >= 2),
    prenom         VARCHAR(30)  NOT NULL CHECK (prenom ~ '^[A-Za-zÀ-ÿ'' -]+$' AND length(trim(prenom)) >= 2),
    telephone      CHAR(10)     NOT NULL CHECK (telephone ~ '^0[1-9][0-9]{8}$'),
    email          VARCHAR(320) NOT NULL UNIQUE
                   CHECK (email ~ '^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$'),
    date_embauche  DATE         NOT NULL CHECK (date_embauche > DATE '1900-01-01' AND date_embauche <= CURRENT_DATE),
    photo          BYTEA        CHECK (photo IS NULL OR octet_length(photo) <= 102400)
);

CREATE TABLE receptionniste (
    id_pers   INTEGER  PRIMARY KEY
              REFERENCES personnel(id_pers) ON DELETE CASCADE ON UPDATE CASCADE,
    num_poste INTEGER  NOT NULL CHECK (num_poste > 0)
);

CREATE TABLE coach (
    id_pers           INTEGER                 PRIMARY KEY
                      REFERENCES personnel(id_pers) ON DELETE CASCADE ON UPDATE CASCADE,
    specialite        specialite_coach_enum   NOT NULL,
    date_certif       DATE                    NOT NULL CHECK (date_certif > DATE '1900-01-01' AND date_certif <= CURRENT_DATE),
    niveau_experience niveau_experience_enum  NOT NULL
);

CREATE TABLE salle (
    id_salle   INTEGER          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nom_salle  VARCHAR(30)      NOT NULL UNIQUE CHECK (length(trim(nom_salle)) > 0),
    type_salle type_salle_enum  NOT NULL,
    capacite   INTEGER          NOT NULL CHECK (capacite > 0),
    superficie NUMERIC(7,2)     NOT NULL CHECK (superficie > 0),
    CONSTRAINT chk_densite_securite CHECK (superficie / capacite >= 1.5)
);

CREATE TABLE cours (
    id_cours     INTEGER           GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nom          VARCHAR(50)       NOT NULL UNIQUE CHECK (length(trim(nom)) > 0),
    jour_semaine jour_semaine_enum NOT NULL,
    niveau       niveau_cours_enum NOT NULL,
    capacite_max INTEGER           NOT NULL CHECK (capacite_max > 0)
);

CREATE TABLE entreprise_partenaire (
    id_entreprise        CHAR(9)      PRIMARY KEY CHECK (id_entreprise ~ '^[A-Za-z0-9]{9}$'),
    nom                  VARCHAR(30)  NOT NULL CHECK (length(trim(nom)) > 0),
    siret                CHAR(14)     NOT NULL UNIQUE CHECK (siret ~ '^[0-9]{14}$'),
    numero_adresse       VARCHAR(5)   NOT NULL CHECK (numero_adresse ~ '^[0-9]{1,5}$'),
    adresse              VARCHAR(45)  NOT NULL CHECK (length(trim(adresse)) > 0),
    code_postal          CHAR(5)      NOT NULL CHECK (code_postal ~ '^[0-9]{5}$'),
    ville                VARCHAR(45)  NOT NULL CHECK (length(trim(ville)) > 0),
    email_contact        VARCHAR(320) NOT NULL UNIQUE
                         CHECK (email_contact ~ '^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$'),
    telephone_contact    CHAR(10)     NOT NULL UNIQUE CHECK (telephone_contact ~ '^0[1-9][0-9]{8}$'),
    debut_partenariat    DATE         NOT NULL CHECK (debut_partenariat <= CURRENT_DATE),
    echeance_partenariat DATE         NOT NULL,
    CONSTRAINT chk_dates_partenariat CHECK (echeance_partenariat > debut_partenariat)
);

CREATE TABLE adherent (
    id_adherent           CHAR(12)            PRIMARY KEY CHECK (id_adherent ~ '^[A-Za-z0-9]{12}$'),
    nom                   VARCHAR(30)         NOT NULL CHECK (nom ~ '^[A-Za-zÀ-ÿ'' -]+$' AND length(trim(nom)) >= 2),
    prenom                VARCHAR(30)         NOT NULL CHECK (prenom ~ '^[A-Za-zÀ-ÿ'' -]+$' AND length(trim(prenom)) >= 2),
    genre                 genre_enum          NOT NULL,
    date_naissance        DATE                NOT NULL CHECK (date_naissance > CURRENT_DATE - INTERVAL '110 years'
                                                              AND date_naissance <= CURRENT_DATE - INTERVAL '15 years'),
    email                 VARCHAR(320)        NOT NULL UNIQUE
                          CHECK (email ~ '^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$'),
    telephone             CHAR(10)            NOT NULL UNIQUE CHECK (telephone ~ '^0[1-9][0-9]{8}$'),
    canal_contact_favoris canal_contact_enum  NOT NULL,
    code_postal           CHAR(5)             NOT NULL CHECK (code_postal ~ '^[0-9]{5}$'),
    ville                 VARCHAR(45)         NOT NULL CHECK (length(trim(ville)) > 0),
    certif_medical_exp    DATE,
    photo                 BYTEA               CHECK (photo IS NULL OR octet_length(photo) <= 102400),
    etat_badge            etat_badge_enum     NOT NULL DEFAULT 'Actif',
    statut_admin          statut_admin_enum   NOT NULL DEFAULT 'actif',
    fin_penalite          DATE,
    CONSTRAINT chk_coherence_penalite CHECK (
        (statut_admin = 'pénalisé' AND fin_penalite IS NOT NULL AND fin_penalite > CURRENT_DATE) OR
        (statut_admin != 'pénalisé' AND fin_penalite IS NULL)
    )
);

CREATE TABLE adherent_etudiant (
    id_adherent           CHAR(12)     PRIMARY KEY
                          REFERENCES adherent(id_adherent) ON DELETE CASCADE ON UPDATE CASCADE,
    mail_etudiant         VARCHAR(320) NOT NULL UNIQUE
                          CHECK (mail_etudiant ~ '^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$'),
    etablissement         VARCHAR(100) NOT NULL CHECK (length(trim(etablissement)) > 0),
    type_formation        VARCHAR(50)  NOT NULL CHECK (length(trim(type_formation)) > 0),
    date_debut_statut_etu DATE         NOT NULL CHECK (date_debut_statut_etu > DATE '2000-01-01' AND date_debut_statut_etu <= CURRENT_DATE),
    date_fin_statut_etu   DATE         NOT NULL,
    CONSTRAINT chk_duree_statut_etudiant CHECK (
        date_fin_statut_etu >= date_debut_statut_etu + INTERVAL '1 year' AND
        date_fin_statut_etu <= date_debut_statut_etu + INTERVAL '5 years'
    )
);

CREATE TABLE adherent_entreprise (
    id_adherent            CHAR(12)           PRIMARY KEY
                           REFERENCES adherent(id_adherent) ON DELETE CASCADE ON UPDATE CASCADE,
    type_contrat           type_contrat_enum  NOT NULL,
    expiration_eligibilite DATE               CHECK (expiration_eligibilite IS NULL OR expiration_eligibilite >= CURRENT_DATE),
    id_entreprise          CHAR(9)            NOT NULL
                           REFERENCES entreprise_partenaire(id_entreprise) ON UPDATE CASCADE
);

CREATE TABLE abonnement (
    id_abonnement       INTEGER                GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    type_abonnement     type_abonnement_enum   NOT NULL,
    date_debut          DATE                   NOT NULL,
    date_fin            DATE                   NOT NULL,
    statut              statut_abonnement_enum NOT NULL DEFAULT 'actif',
    delai_grace_jours   INTEGER                NOT NULL DEFAULT 7 CHECK (delai_grace_jours BETWEEN 0 AND 30),
    id_adherent         CHAR(12)               NOT NULL REFERENCES adherent(id_adherent)
                                               ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT chk_duree_abonnement CHECK (date_fin >= date_debut + INTERVAL '1 day')
);

CREATE TABLE paiement (
    id_paiement   INTEGER            GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    date_paiement DATE               NOT NULL CHECK (date_paiement <= CURRENT_DATE),
    montant       NUMERIC(6,2)       NOT NULL CHECK (montant BETWEEN 0.01 AND 5000.00),
    mode_paiement mode_paiement_enum NOT NULL,
    id_abonnement INTEGER            NOT NULL
                  REFERENCES abonnement(id_abonnement) ON UPDATE CASCADE
);

CREATE TABLE seance (
    id_seance         INTEGER  GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    date_seance       DATE     NOT NULL,
    heure_debut       TIME(0)  NOT NULL CHECK (heure_debut >= TIME '06:00:00' AND heure_debut <= TIME '22:00:00'),
    heure_fin         TIME(0)  NOT NULL,
    id_salle          INTEGER  NOT NULL REFERENCES salle(id_salle) ON UPDATE CASCADE ON DELETE CASCADE,
    id_coach          INTEGER  NOT NULL REFERENCES coach(id_pers) ON UPDATE CASCADE,
    id_receptionniste INTEGER  NOT NULL REFERENCES receptionniste(id_pers)
                               ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT chk_duree_seance CHECK (
        heure_fin > heure_debut AND
        (heure_fin - heure_debut) BETWEEN INTERVAL '15 minutes' AND INTERVAL '4 hours'
    )
);

CREATE TABLE programmer (
    id_seance INTEGER PRIMARY KEY REFERENCES seance(id_seance) ON DELETE CASCADE ON UPDATE CASCADE,
    id_cours  INTEGER NOT NULL    REFERENCES cours(id_cours)   ON UPDATE CASCADE
);

CREATE TABLE qualifier (
    id_pers_coach INTEGER NOT NULL REFERENCES coach(id_pers) ON DELETE CASCADE ON UPDATE CASCADE,
    id_cours      INTEGER NOT NULL REFERENCES cours(id_cours) ON DELETE CASCADE ON UPDATE CASCADE,
    date_obtention DATE   NOT NULL CHECK (date_obtention <= CURRENT_DATE),
    PRIMARY KEY (id_pers_coach, id_cours)
);

CREATE TABLE reserver (
    id_seance        INTEGER                  NOT NULL REFERENCES seance(id_seance) ON DELETE CASCADE ON UPDATE CASCADE,
    id_adherent      CHAR(12)                 NOT NULL REFERENCES adherent(id_adherent) ON DELETE CASCADE ON UPDATE CASCADE,
    date_reservation TIMESTAMP(0)             NOT NULL DEFAULT CURRENT_TIMESTAMP,
    statut           statut_reservation_enum NOT NULL DEFAULT 'confirmée',
    PRIMARY KEY (id_seance, id_adherent)
);

CREATE TABLE presence (
    id_presence         INTEGER      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_adherent         CHAR(12)     NOT NULL REFERENCES adherent(id_adherent) ON DELETE CASCADE ON UPDATE CASCADE,
    date_heure_entree   TIMESTAMP(0) NOT NULL CHECK (date_heure_entree <= CURRENT_TIMESTAMP),
    date_heure_sortie   TIMESTAMP(0),
    delai_grace_utilise BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT chk_coherence_presence CHECK (
        date_heure_sortie IS NULL OR
        (date_heure_sortie > date_heure_entree AND date_heure_sortie <= date_heure_entree + INTERVAL '24 hours')
    )
);

CREATE TABLE borne (
    id_borne    INTEGER           GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    type_borne  type_borne_enum   NOT NULL,
    statut      statut_borne_enum NOT NULL DEFAULT 'active',
    emplacement VARCHAR(50)       NOT NULL CHECK (length(trim(emplacement)) > 0)
);

-- ---------------------------------------------------------------------
-- Index
-- ---------------------------------------------------------------------
CREATE INDEX idx_abonnement_adherent ON abonnement(id_adherent);
CREATE INDEX idx_paiement_abonnement ON paiement(id_abonnement);
CREATE INDEX idx_seance_salle        ON seance(id_salle);
CREATE INDEX idx_seance_coach        ON seance(id_coach);
CREATE INDEX idx_seance_date         ON seance(date_seance);
CREATE INDEX idx_presence_adherent   ON presence(id_adherent, date_heure_entree);
CREATE INDEX idx_adh_entreprise_ent  ON adherent_entreprise(id_entreprise);

-- ---------------------------------------------------------------------
-- Déclencheurs d'Exclusivité (Héritage)
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION check_exclusive() RETURNS trigger AS $$
DECLARE
    clash boolean;
BEGIN
    EXECUTE format('SELECT EXISTS (SELECT 1 FROM %I WHERE %I::text = $1)', TG_ARGV[0], TG_ARGV[1])
       INTO clash USING (to_jsonb(NEW) ->> TG_ARGV[1]);
    IF clash THEN
        RAISE EXCEPTION 'Violation d''exclusivité : % existe déjà dans la table %', NEW, TG_ARGV[0];
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
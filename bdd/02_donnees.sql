-- ---------------------------------------------------------------------
-- 1. PERSONNEL, COACH ET RECEPTIONNISTE
-- ---------------------------------------------------------------------
INSERT INTO personnel (id_pers, nom, prenom, telephone, email, date_embauche, photo) VALUES
(11200451, 'Martin', 'Julien', '0765453287', 'j.martin@club.fr', '2025-08-12', NULL),
(11200452, 'Benali', 'Samira', '0612345098', 's.benali@club.fr', '2024-09-03', NULL),
(11200453, 'Lefèvre', 'Camille', '0698214530', 'c.lefevre@club.fr', '2023-01-15', NULL),
(11200454, 'Moreau', 'Thomas', '0745328190', 't.moreau@club.fr', '2026-03-01', NULL),
(11200455, 'Nguyen', 'Linh', '0756091234', 'l.nguyen@club.fr', '2022-09-20', NULL),
(11200456, 'Diallo', 'Moussa', '0623789045', 'm.diallo@club.fr', '2021-02-05', NULL),
(11200457, 'Rossi', 'Élodie', '0687452310', 'e.rossi@club.fr', '2026-06-18', NULL),
(11200458, 'Garcia', 'Lucas', '0634567812', 'l.garcia@club.fr', '2026-09-07', NULL),
(11200459, 'Petit', 'Inès', '0771203456', 'i.petit@club.fr', '2023-11-02', NULL);

INSERT INTO coach (id_pers, specialite, date_certif, niveau_experience) VALUES
(11200451, 'Musculation', '2012-02-22', 'expert'),
(11200453, 'Yoga', '2019-06-10', 'confirmé'),
(11200455, 'Pilates', '2020-11-14', 'intermédiaire'),
(11200456, 'Boxe', '2015-03-30', 'expert'),
(11200458, 'Cross-training', '2025-05-12', 'débutant'),
(11200459, 'Zumba', '2022-08-19', 'intermédiaire');

INSERT INTO receptionniste (id_pers, num_poste) VALUES
(11200452, 1),
(11200454, 2),
(11200457, 3);

-- ---------------------------------------------------------------------
-- 2. SALLE ET COURS
-- ---------------------------------------------------------------------
INSERT INTO salle (id_salle, nom_salle, type_salle, capacite, superficie) OVERRIDING SYSTEM VALUE VALUES
(1, 'Salle Zen', 'Studio bien-être', 25, 60.00),
(2, 'Salle Force', 'Musculation', 40, 150.00),
(3, 'Salle Cardio+', 'Cardio-training', 35, 120.50),
(4, 'Studio Énergie', 'Cours collectifs', 30, 90.00),
(5, 'Ring Alpha', 'Sports de combat', 16, 75.25);

INSERT INTO cours (id_cours, nom, jour_semaine, niveau, capacite_max) OVERRIDING SYSTEM VALUE VALUES
(1, 'Yoga débutant', 'Lundi', 'débutant', 20),
(2, 'Pilates tonique', 'Mardi', 'intermédiaire', 20),
(3, 'Boxe cardio', 'Mercredi', 'intermédiaire', 16),
(4, 'Zumba fitness', 'Jeudi', 'débutant', 25),
(5, 'CrossFit WOD', 'Vendredi', 'avancé', 15);

-- ---------------------------------------------------------------------
-- 3. ENTREPRISE_PARTENAIRE ET ADHERENTS
-- ---------------------------------------------------------------------
INSERT INTO entreprise_partenaire (id_entreprise, nom, siret, numero_adresse, adresse, code_postal, ville, email_contact, telephone_contact, debut_partenariat, echeance_partenariat) VALUES
('2C3D4E001', 'Technova Solutions', '81234567800015', '24', 'Avenue des Champs', '95000', 'Cergy', 'contact@technova.fr', '0134256789', '2024-03-15', '2029-03-15'),
('6H7J8K002', 'Biolab Pharma', '52345678900027', '5', 'Boulevard de l''Oise', '95300', 'Pontoise', 'rh@biolab.com', '0139804512', '2025-09-01', '2027-09-01'),
('10N1P2Q03', 'Transports Durand', '43456789100034', '120', 'Rue des Lilas', '95800', 'Cergy', 'admin@durand.fr', '0130384721', '2026-01-10', '2031-01-10'),
('4T5U6V004', 'Banque Horizon', '34567891200041', '8', 'Place de la République', '75010', 'Paris', 'part@horizon.fr', '0142785634', '2023-06-20', '2028-06-20');
INSERT INTO adherent (id_adherent, nom, prenom, genre, date_naissance, email, telephone, canal_contact_favoris, code_postal, ville, certif_medical_exp, photo, etat_badge, statut_admin, fin_penalite) VALUES
('K82mQ41R09ab', 'Bernard', 'Sophie', 'F', '1992-03-14', 'sophie.b@gmail.com', '0612457801', 'email', '95000', 'Cergy', '2027-06-15', NULL, 'Actif', 'actif', NULL),
('P19xZ7cL33wd', 'Lambert', 'Hugo', 'M', '1985-11-02', 'h.lambert@outlook.fr', '0678123490', 'telephone', '95300', 'Pontoise', NULL, NULL, 'Actif', 'actif', NULL),
('TS5hNZv880ge', 'Roux', 'Karim', 'M', '1999-07-23', 'karim.roux@yahoo.fr', '0745098123', 'telephone', '78000', 'Versailles', '2027-01-30', NULL, 'Inactif', 'pénalisé', '2026-10-15'),
('W03yD8sF71ik', 'Fontaine', 'Léa', 'F', '1978-05-30', 'lea.fontaine@mail.me', '0623908451', 'email', '75010', 'Paris', '2026-12-12', NULL, 'Actif', 'actif', NULL),
('562nC1gY480x', 'Haddad', 'Yasmine', 'F', '2003-04-18', 'y.haddad@gmail.com', '0689345012', 'email', '95800', 'Cergy', '2027-09-05', NULL, 'Actif', 'actif', NULL),
('M31rE6uH27tz', 'Petit', 'Nathan', 'M', '2005-01-09', 'nathan.p@hotmail.fr', '0756234109', 'telephone', '95520', 'Osny', NULL, NULL, 'Actif', 'actif', NULL),
('D90bV5kI64sq', 'Chen', 'Lina', 'F', '2002-12-21', 'lina.chen@mail.me', '0634781290', 'email', '78000', 'Versailles', '2027-03-20', NULL, 'Inactif', 'banni', NULL),
('G47pL0wA92nc', 'Girard', 'Antoine', 'M', '1988-06-05', 'a.girard@gmail.com', '0601927345', 'email', '95000', 'Cergy', '2026-11-18', NULL, 'Actif', 'actif', NULL),
('H85zQ3dM16ys', 'Mercier', 'Julie', 'F', '1995-02-17', 'julie.m@orange.fr', '0667094512', 'telephone', '95300', 'Pontoise', NULL, NULL, 'Actif', 'actif', NULL),
('N26uJ8eT59kv', 'Barbier', 'Olivier', 'M', '1980-08-12', 'o.barbier@free.fr', '0712056389', 'email', '75010', 'Paris', '2027-05-02', NULL, 'Actif', 'actif', NULL);

INSERT INTO adherent_etudiant (id_adherent, mail_etudiant, etablissement, type_formation, date_debut_statut_etu, date_fin_statut_etu) VALUES
('562nC1gY480x', 'y.haddad@etu.cyu.fr', 'CY Cergy Paris', 'Licence Informatique', '2024-09-01', '2027-09-01'),
('M31rE6uH27tz', 'nathan.p@etu.univ-paris13.fr', 'Université Paris 13', 'Master Biologie', '2025-09-01', '2027-09-01'),
('D90bV5kI64sq', 'lina.c@etu.uvsq.fr', 'UVSQ', 'BUT Informatique', '2024-09-01', '2027-09-01');

INSERT INTO adherent_entreprise (id_adherent, type_contrat, expiration_eligibilite, id_entreprise) VALUES
('G47pL0wA92nc', 'CDI', '2028-12-31', '2C3D4E001'),
('H85zQ3dM16ys', 'CDD', '2027-06-30', '6H7J8K002'),
('N26uJ8eT59kv', 'Intérim', NULL, '10N1P2Q03');

-- ---------------------------------------------------------------------
-- 4. ABONNEMENT ET PAIEMENT
-- ---------------------------------------------------------------------
INSERT INTO abonnement (id_abonnement, type_abonnement, date_debut, date_fin, statut, delai_grace_jours, id_adherent) OVERRIDING SYSTEM VALUE VALUES
(1, 'annuel', '2026-01-15', '2027-01-15', 'actif', 7, 'K82mQ41R09ab'),
(2, 'mensuel', '2026-09-15', '2026-10-15', 'actif', 3, 'P19xZ7cL33wd'),
(3, 'annuel', '2025-06-01', '2026-06-01', 'expiré', 7, 'TS5hNZv880ge'),
(4, 'trimestriel', '2026-08-01', '2026-11-01', 'actif', 5, 'W03yD8sF71ik'),
(5, 'mensuel', '2026-09-05', '2026-10-05', 'expiré', 3, '562nC1gY480x'),
(6, 'annuel', '2026-09-01', '2027-09-01', 'actif', 7, 'M31rE6uH27tz'),
(7, 'trimestriel', '2026-04-01', '2026-07-01', 'suspendu', 5, 'D90bV5kI64sq'),
(8, 'annuel', '2026-02-15', '2027-02-15', 'actif', 7, 'G47pL0wA92nc'),
(9, 'mensuel', '2026-10-05', '2026-11-05', 'actif', 3, 'H85zQ3dM16ys'),
(10, 'mensuel', '2026-10-01', '2026-11-01', 'actif', 3, 'N26uJ8eT59kv');

INSERT INTO paiement (id_paiement, date_paiement, montant, mode_paiement, id_abonnement) OVERRIDING SYSTEM VALUE VALUES
(1, '2026-01-15', 299.90, 'virement', 1),
(2, '2026-09-15', 29.90, 'carte', 2),
(3, '2025-06-01', 299.90, 'chèque', 3),
(4, '2026-08-01', 79.90, 'carte', 4),
(5, '2026-09-05', 29.90, 'espèces', 5),
(6, '2026-09-01', 299.90, 'virement', 6),
(7, '2026-04-01', 79.90, 'carte', 7),
(8, '2026-02-15', 299.90, 'virement', 8),
(9, '2026-10-05', 29.90, 'espèces', 9),
(10, '2026-10-01', 29.90, 'carte', 10);

-- ---------------------------------------------------------------------
-- 5. GESTION DES SEANCES ET RESERVATIONS
-- ---------------------------------------------------------------------
INSERT INTO seance (id_seance, date_seance, heure_debut, heure_fin, id_salle, id_coach, id_receptionniste) OVERRIDING SYSTEM VALUE VALUES
(1, '2026-10-12', '18:00:00', '19:00:00', 1, 11200453, 11200452),
(2, '2026-10-13', '12:30:00', '13:30:00', 4, 11200455, 11200454),
(3, '2026-10-14', '19:00:00', '20:30:00', 5, 11200456, 11200457),
(4, '2026-10-15', '18:00:00', '19:00:00', 4, 11200459, 11200452),
(5, '2026-10-16', '12:00:00', '13:00:00', 2, 11200451, 11200454),
(6, '2026-10-17', '10:00:00', '11:00:00', 1, 11200453, 11200457);

INSERT INTO programmer (id_seance, id_cours) VALUES
(1, 1),
(2, 2),
(3, 3),
(4, 4),
(5, 5),
(6, 1);

INSERT INTO qualifier (id_pers_coach, id_cours, date_obtention) VALUES
(11200453, 1, '2019-06-15'),
(11200455, 2, '2020-12-01'),
(11200456, 3, '2015-05-20'),
(11200459, 4, '2022-08-19'),
(11200451, 5, '2018-09-10'),
(11200458, 5, '2025-05-12'),
(11200453, 2, '2021-03-22');

INSERT INTO reserver (id_seance, id_adherent, date_reservation, statut) VALUES
(1, 'K82mQ41R09ab', '2026-10-05 10:00:00', 'confirmée'),
(1, 'W03yD8sF71ik', '2026-10-06 14:30:00', 'confirmée'),
(2, 'G47pL0wA92nc', '2026-10-08 09:15:00', 'en_attente'),
(2, '562nC1gY480x', '2026-10-08 10:00:00', 'confirmée'),
(3, 'P19xZ7cL33wd', '2026-10-09 11:00:00', 'confirmée'),
(3, 'M31rE6uH27tz', '2026-10-09 16:20:00', 'annulée'),
(4, 'H85zQ3dM16ys', '2026-10-10 08:00:00', 'confirmée'),
(5, 'N26uJ8eT59kv', '2026-10-10 14:00:00', 'en_attente'),
(6, 'K82mQ41R09ab', '2026-10-11 09:00:00', 'confirmée'),
(6, 'W03yD8sF71ik', '2026-10-11 09:15:00', 'confirmée');

-- ---------------------------------------------------------------------
-- 6. PRESENCE ET BORNE
-- ---------------------------------------------------------------------
INSERT INTO presence (id_presence, id_adherent, date_heure_entree, date_heure_sortie, delai_grace_utilise) OVERRIDING SYSTEM VALUE VALUES
(1, 'K82mQ41R09ab', '2026-10-08 17:45:00', '2026-10-08 19:15:00', FALSE),
(2, 'P19xZ7cL33wd', '2026-10-09 12:15:00', '2026-10-09 13:40:00', TRUE),
(3, '562nC1gY480x', '2026-10-09 18:00:00', '2026-10-09 19:30:00', FALSE),
(4, 'G47pL0wA92nc', '2026-10-10 08:50:00', '2026-10-10 10:15:00', FALSE),
(5, 'H85zQ3dM16ys', '2026-10-10 17:55:00', '2026-10-10 19:10:00', FALSE),
(6, 'N26uJ8eT59kv', '2026-10-11 09:45:00', '2026-10-11 11:30:00', FALSE),
(7, 'K82mQ41R09ab', '2026-10-11 17:30:00', '2026-10-11 19:00:00', FALSE),
(8, 'W03yD8sF71ik', '2026-10-11 17:40:00', '2026-10-11 19:10:00', FALSE);

INSERT INTO borne (id_borne, type_borne, statut, emplacement) OVERRIDING SYSTEM VALUE VALUES
(1, 'controle_acces', 'active', 'Entrée principale'),
(2, 'salle_seance', 'active', 'Entrée Salle Zen'),
(3, 'controle_acces', 'désactivée', 'Portique secondaire'),
(4, 'salle_seance', 'active', 'Entrée Studio Énergie'),
(5, 'salle_seance', 'active', 'Entrée Salle Force'),
(6, 'controle_acces', 'active', 'Parking souterrain');


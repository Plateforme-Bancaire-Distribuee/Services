-- Script d'initialisation des bases de données PostgreSQL
-- Utilisé lors du démarrage du conteneur PostgreSQL

-- Créer les bases de données si elles n'existent pas
CREATE DATABASE account_db;
CREATE DATABASE loan_db;
CREATE DATABASE transaction_db;
CREATE DATABASE document_db;

-- Créer des utilisateurs spécifiques par service (optionnel, pour plus de sécurité)
-- CREATE USER account_user WITH PASSWORD 'account_pass';
-- CREATE USER loan_user WITH PASSWORD 'loan_pass';
-- CREATE USER transaction_user WITH PASSWORD 'transaction_pass';
-- CREATE USER document_user WITH PASSWORD 'document_pass';

-- Donner les permissions
-- ALTER DATABASE account_db OWNER TO account_user;
-- ALTER DATABASE loan_db OWNER TO loan_user;
-- ALTER DATABASE transaction_db OWNER TO transaction_user;
-- ALTER DATABASE document_db OWNER TO document_user;

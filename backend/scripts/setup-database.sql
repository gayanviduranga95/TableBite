-- =====================================================================
-- One-time PostgreSQL setup for TableBite (run as a superuser, e.g. postgres):
--
--   psql -U postgres -f backend/scripts/setup-database.sql
--
-- You only need this for a NEW installation. Tables, seed data and all
-- upgrades are created automatically by the backend on start-up
-- (src/main/resources/database/migration/V1..V4, all idempotent).
-- If you already have a tablebite_db database, skip this file.
-- =====================================================================

-- 1. Application role (change the password!)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'tablebite_app') THEN
        CREATE ROLE tablebite_app LOGIN PASSWORD 'change-this-password';
    END IF;
END $$;

-- 2. Database owned by the application role
SELECT 'CREATE DATABASE tablebite_db OWNER tablebite_app ENCODING ''UTF8'''
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'tablebite_db')
\gexec

-- Then start the backend with:
--   DB_USER=tablebite_app  DB_PASSWORD=change-this-password
--
-- Using an existing database owned by another role? Make sure DB_USER owns the
-- TableBite tables (the migrations use ALTER TABLE), for example:
--   ALTER TABLE restaurant_tables OWNER TO tablebite_app;  -- etc.

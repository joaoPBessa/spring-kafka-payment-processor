-- User to flyway application
CREATE USER "payment-admin" WITH PASSWORD 'qU0+ketjd]65]q7D' SUPERUSER;

-- User to payment-producer-api
CREATE USER "payment-producer-app" WITH PASSWORD 'K1SJdZqbkJyW^qM)';

-- GRANTs to users
GRANT ALL PRIVILEGES ON DATABASE payments_db TO "payment-admin";
GRANT CONNECT ON DATABASE payments_db TO "payment-producer-app";

-- Create project schema and grants
CREATE SCHEMA IF NOT EXISTS PAYMENT;

GRANT ALL ON SCHEMA PAYMENT TO "payment-admin";
GRANT USAGE ON SCHEMA PAYMENT TO "payment-producer-app";

REVOKE ALL ON SCHEMA public FROM PUBLIC;
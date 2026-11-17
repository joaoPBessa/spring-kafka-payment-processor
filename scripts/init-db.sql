-- User to flyway application
CREATE USER "payment-admin" WITH PASSWORD 'qU0+ketjd]65]q7D' SUPERUSER;

-- User to payment-producer-api
CREATE USER "payment-producer-app" WITH PASSWORD 'K1SJdZqbkJyW^qM)';

-- User to payment-consumer-api
CREATE USER "payment-consumer-app" WITH PASSWORD 'Zx7mP4rQd9LkV2nT';

-- User to payment-auth-server (reads the application users; table grants are in the Flyway migrations)
CREATE USER "payment-auth-app" WITH PASSWORD 'Hq5vN8cWm3TbJ6sE';

-- GRANTs to users
GRANT ALL PRIVILEGES ON DATABASE payments_db TO "payment-admin";
GRANT CONNECT ON DATABASE payments_db TO "payment-producer-app";
GRANT CONNECT ON DATABASE payments_db TO "payment-consumer-app";
GRANT CONNECT ON DATABASE payments_db TO "payment-auth-app";

-- Create project schema and grants
CREATE SCHEMA IF NOT EXISTS PAYMENT;

GRANT ALL ON SCHEMA PAYMENT TO "payment-admin";
GRANT USAGE ON SCHEMA PAYMENT TO "payment-producer-app";
GRANT USAGE ON SCHEMA PAYMENT TO "payment-consumer-app";
GRANT USAGE ON SCHEMA PAYMENT TO "payment-auth-app";

REVOKE ALL ON SCHEMA public FROM PUBLIC;
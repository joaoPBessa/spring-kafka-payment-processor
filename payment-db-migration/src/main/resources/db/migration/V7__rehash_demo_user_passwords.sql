-- DEMONSTRATION USERS ONLY. V6 seeded their passwords Base64-encoded, which is an encoding and not a hash: anyone who
-- can read the table can read the passwords. This migration replaces them with bcrypt hashes (cost 10).
-- The password of each user is still "<username>-demo", and still public; only how it is stored changes.
-- V6 is left as it was, because Flyway checksums the migrations that were already applied.
-- A password that is no longer Base64 (because someone changed it) is left alone.

UPDATE APP_USER
SET PASSWORD = '{bcrypt}$2a$10$WS43iHM.pgH19synMInLS.7PVkzOiWGza1g5EnSy7G4K/O365l01i'
WHERE USERNAME = 'producer-client' AND PASSWORD LIKE '{base64}%';

UPDATE APP_USER
SET PASSWORD = '{bcrypt}$2a$10$LI9fL5PfppzOiHrcJ6Fvu.Vds6978FxT4R8R5nzKIOGRs0tZ4jjNe'
WHERE USERNAME = 'producer-reader' AND PASSWORD LIKE '{base64}%';

UPDATE APP_USER
SET PASSWORD = '{bcrypt}$2a$10$k1JN78edewVN4uulYVecYOFlXJkdYEEHLr0ulPqYjrXbsw2CsjVeK'
WHERE USERNAME = 'dlt-operator' AND PASSWORD LIKE '{base64}%';

UPDATE APP_USER
SET PASSWORD = '{bcrypt}$2a$10$SILJ7/mpBEWY71cq/mN08eJH7ocAQjdUeCQSNvVrypYX40pgOyL06'
WHERE USERNAME = 'dlt-viewer' AND PASSWORD LIKE '{base64}%';

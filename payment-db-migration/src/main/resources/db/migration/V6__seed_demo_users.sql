-- DEMONSTRATION USERS ONLY. Their passwords are public (see the READMEs) and stored Base64-encoded, which is an
-- encoding and not a hash. Replace this migration with real user provisioning before any real use.
-- The password of each user is "<username>-demo".

INSERT INTO APP_USER (ID, USERNAME, PASSWORD) VALUES
    (gen_random_uuid(), 'producer-client', '{base64}cHJvZHVjZXItY2xpZW50LWRlbW8='),
    (gen_random_uuid(), 'producer-reader', '{base64}cHJvZHVjZXItcmVhZGVyLWRlbW8='),
    (gen_random_uuid(), 'dlt-operator', '{base64}ZGx0LW9wZXJhdG9yLWRlbW8='),
    (gen_random_uuid(), 'dlt-viewer', '{base64}ZGx0LXZpZXdlci1kZW1v');

INSERT INTO APP_USER_ROLE (USER_ID, ROLE)
SELECT ID, ROLE
FROM APP_USER
         JOIN (VALUES ('producer-client', 'ACCOUNT_READ'),
                      ('producer-client', 'ACCOUNT_WRITE'),
                      ('producer-client', 'PAYMENT_CREATE'),
                      ('producer-reader', 'ACCOUNT_READ'),
                      ('dlt-operator', 'DLT_VIEW'),
                      ('dlt-operator', 'DLT_REPLAY'),
                      ('dlt-viewer', 'DLT_VIEW')) AS DEMO_ROLE (USERNAME, ROLE)
              ON DEMO_ROLE.USERNAME = APP_USER.USERNAME;

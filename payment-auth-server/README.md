# Payment Auth Server

The OAuth2 authorization server of the platform. It signs people in against the user tables, issues short-lived JWT access tokens, and publishes the public key the applications use to validate them. It is built on Spring Authorization Server and listens on port **9000**.

The producer and the consumer are resource servers: they only validate the tokens (signature through `/oauth2/jwks`, issuer, expiry) and never see a user table or a password. The shared resource server configuration lives in `common-libraries`.

## Flows

| Client | Flow | Used for | Roles in the token |
|---|---|---|---|
| `payments-cli` | Authorization code with PKCE (public client, no secret) | People. The login form checks the user tables. | The roles stored for the user |
| `prometheus` | Client credentials | The Prometheus scrape of `/actuator/prometheus` | `METRICS_READ` |
| `traffic-generator` | Client credentials | The load script of the observability stack | `ACCOUNT_READ`, `ACCOUNT_WRITE`, `PAYMENT_CREATE` |

The password grant is not offered: OAuth 2.1 removed it, and Spring Authorization Server does not implement it.

An access token is a signed JWT (RS256) that lives **15 minutes**; there is no refresh token. Besides the standard claims it carries `roles`, a list of role names such as `["DLT_VIEW", "DLT_REPLAY"]`. The resource servers turn each entry into a `ROLE_*` authority, so `@PreAuthorize("hasRole('DLT_VIEW')")` works the same for a person and for a service.

## Users and roles

Users live in `APP_USER` and `APP_USER_ROLE` (Flyway `V5`). `V6` seeds four demonstration users; the password of each is `<username>-demo`.

| User | Roles | Meant for |
|---|---|---|
| `producer-client` | `ACCOUNT_READ`, `ACCOUNT_WRITE`, `PAYMENT_CREATE` | Everything the producer offers |
| `producer-reader` | `ACCOUNT_READ` | Reading accounts only |
| `dlt-operator` | `DLT_VIEW`, `DLT_REPLAY` | Looking at and replaying the dead letters |
| `dlt-viewer` | `DLT_VIEW` | Looking at the dead letters only |

> **The stored passwords are Base64, which is an encoding, not a hash.** Anyone who can read the table can read the passwords. It is a deliberate stand-in for the example (`Base64PasswordEncoder` in `common-libraries` says so in its Javadoc). Values are stored as `{base64}...` behind a `DelegatingPasswordEncoder`, so a real hash such as `{bcrypt}` can be added under its own id and used for new passwords without migrating the existing ones.

## Getting a token

```bash
# A person (authorization code + PKCE, without a browser)
TOKEN=$(scripts/get-token.sh producer-client producer-client-demo)
curl -H "Authorization: Bearer $TOKEN" http://localhost:8081/api/v1/accounts
```

`scripts/get-token.sh` does what a browser would do: it asks for authorization, signs in on the login form with the CSRF token, follows the redirect to the callback to read the code, and exchanges the code with the PKCE verifier. The password is a command line argument, so use it with the demonstration users only.

A service uses client credentials; its secret is in Vault:

```bash
curl -u prometheus:<secret> -d grant_type=client_credentials http://localhost:9000/oauth2/token
```

Other endpoints: `/oauth2/jwks` (the public key only), `/.well-known/oauth-authorization-server` (metadata) and `/actuator/health`.

## Where the credentials live

Nothing secret is in an application file of this module:

- **Database:** the module connects as its own role, `payment-auth-app`, which can `SELECT` the two user tables and nothing else (no write, no access to `ACCOUNT` or `PAYMENT`, no schema `CREATE`). The producer and the consumer cannot read the user tables. Only the JDBC URL is in `application.yaml`.
- **Vault:** the role's username and password and the client secrets of `prometheus` and `traffic-generator` are read from `secret/payment-auth-server` (seeded by `scripts/init-vault.sh` for local development). Without them the application does not start: the settings are validated, and a missing secret is an error, not an empty default.
- **Not exposed:** Actuator publishes only `health` and `info` (no `env`, `configprops` or `beans`), `AuthServerProperties` never prints a secret, and `/oauth2/jwks` only publishes the public key. `NoCredentialsInConfigTest` fails if a username, password or secret appears in a main configuration file.

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `app.auth.issuer` | `http://localhost:9000` | The `iss` claim and the issuer of the metadata |
| `app.auth.access-token-time-to-live` | `15m` | Lifetime of an access token |
| `app.auth.clients.payments-cli.redirect-uri` | `http://127.0.0.1:8085/callback` | The only redirect the public client may use |
| `app.auth.clients.<client>.roles` | per client | Roles a service client gets |
| `app.auth.clients.<client>.secret` | from Vault | Secret of a service client |

## Running it

```bash
docker compose up -d --wait     # Postgres, db-migrate, Vault (seeded), ...
cd payment-auth-server
mvn spring-boot:run
```

`init-db.sql` only runs on an empty Postgres volume, so a volume created before this module existed has no `payment-auth-app` role. Recreate it once: `docker compose rm -sf postgres && docker volume rm spring-kafka-payment-processor_postgres_data`, then `docker compose up -d --wait` again.

## Limitations

- The signing key is generated at startup, so a restart invalidates the tokens already issued (the applications fetch the new key from `/oauth2/jwks`). Persisting the key is out of scope.
- The clients are registered in memory from the settings; the user tables are read-only for this module, so there is no user management, password change or consent screen.
- Tokens cannot be revoked before they expire; that is what the short lifetime is for.

## Tests

- A real Postgres: the repository mapping against the Flyway schema (`ddl-auto: validate`) and the seeded users.
- A full-context integration test on a real Postgres, running the pool as `payment-auth-app`: client credentials, the whole authorization code + PKCE exchange for a seeded user (roles taken from the database), wrong password, unknown and disabled users, a wrong PKCE verifier, the published key, the token lifetime, and a write to the user table being refused.
- Unit tests for the user lookup, the `roles` claim and the settings validation, plus the architecture, package-naming, Vault and no-credentials guards.

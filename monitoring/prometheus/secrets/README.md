# Prometheus client secret

`prometheus-client-secret` holds the secret of the `prometheus` OAuth2 client, which Prometheus uses to get a token
(client credentials) before scraping `/actuator/prometheus`. It is mounted into the container by
`docker-compose.yml` and read through `client_secret_file` in `../prometheus.yml`, so the secret is not inline in the
Prometheus configuration.

The value is the local-development secret that `scripts/init-vault.sh` seeds into Vault
(`app.auth.clients.prometheus.secret` in `secret/payment-auth-server`). Prometheus cannot read Vault, so the two must
be kept in sync by hand. Like the other demonstration credentials of this repository, it is a throwaway
local-development value and must not be reused anywhere real.

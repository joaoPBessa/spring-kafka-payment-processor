#!/bin/sh

# Define the correct URL pointing to the Vault container before the loop
export VAULT_ADDR='http://vault:8200'
export VAULT_TOKEN='root-token-local'

echo "⏳ Waiting for Vault to start..."
# Loop using the correct internal network endpoint
until vault status > /dev/null 2>&1; do
  sleep 1
done

echo "🔓 Vault is ready and accessible!"

echo "📂 Enabling KV secrets engine version 2..."
# If already enabled by dev mode, '|| true' prevents the script from breaking
vault secrets enable -path=secret kv-v2 || true

echo "🔑 Seeding Postgres secrets into Vault..."
vault kv put secret/spring-kafka-payment-processor \
  spring.datasource.username="postgres" \
  spring.datasource.password="5@hbQqkGB<" \
  spring.flyway.user="payment-admin" \
  spring.flyway.password="qU0+ketjd]65]q7D"

echo "✅ Vault configuration completed successfully!"
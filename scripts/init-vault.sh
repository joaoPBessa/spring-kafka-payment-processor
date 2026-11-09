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

echo "🔑 Seeding per-application database credentials into Vault..."
vault kv put secret/payment-producer-api \
  spring.datasource.username="payment-producer-app" \
  spring.datasource.password="K1SJdZqbkJyW^qM)"

vault kv put secret/payment-consumer-api \
  spring.datasource.username="payment-consumer-app" \
  spring.datasource.password="Zx7mP4rQd9LkV2nT"

echo "🧹 Removing the legacy shared secret..."
vault kv metadata delete secret/spring-kafka-payment-processor || true

echo "✅ Vault configuration completed successfully!"
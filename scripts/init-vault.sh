#!/bin/sh

# CORREÇÃO: Define a URL correta apontando para o container do Vault antes do loop
export VAULT_ADDR='http://vault:8200'
export VAULT_TOKEN='root-token-local'

echo "⏳ Aguardando o Vault iniciar..."
# Loop utilizando o endpoint correto da rede interna
until vault status > /dev/null 2>&1; do
  sleep 1
done

echo "🔓 Vault está pronto e acessível!"

echo "📂 Habilitando o motor de segredos KV versão 2..."
# Se já estiver ativado pelo modo dev, o '|| true' evita que o script quebre
vault secrets enable -path=secret kv-v2 || true

echo "🔑 Injetando segredos do Postgres no Vault..."
vault kv put secret/spring-kafka-payment-processor \
  spring.datasource.username="postgres" \
  spring.datasource.password="5@hbQqkGB<"

echo "✅ Configuração do Vault concluída com sucesso!"
#!/usr/bin/env bash
set -euo pipefail
umask 077

if [[ $# -lt 1 || $# -gt 2 ]]; then
    echo "Uso: $0 https://demo.seu-dominio.com [arquivo-de-saida]" >&2
    exit 1
fi

origin=${1%/}
if [[ ! $origin =~ ^https?://[a-zA-Z0-9.-]+(:[0-9]+)?$ ]]; then
    echo "Informe somente a origem HTTP(S), sem caminho, usuario ou parametros." >&2
    exit 1
fi
if [[ $origin == http://* && ! $origin =~ ^http://(localhost|127\.0\.0\.1)(:[0-9]+)?$ ]]; then
    echo "Use HTTPS no deploy publico. HTTP e permitido apenas para testes locais." >&2
    exit 1
fi
if [[ $origin == https://* && ${origin#https://} == *:* ]]; then
    echo "Use HTTPS na porta padrao 443, sem porta na URL." >&2
    exit 1
fi

repo_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
output=${2:-$repo_dir/.env.deploy}
command -v openssl >/dev/null
database_password=$(openssl rand -hex 24)
jwt_secret=$(openssl rand -hex 48)
encryption_key=$(openssl rand -base64 32)
site_address=$origin
if [[ $origin == https://* ]]; then
    site_address=${origin#https://}
else
    authority=${origin#http://}
    site_address=http://${authority%%:*}
fi

# Refuse replacement: changing these keys would invalidate tokens and credentials.
set -o noclobber
cat > "$output" <<EOF
SITE_ADDRESS=$site_address
FRONTEND_URL=$origin
HTTP_PORT=80
HTTPS_PORT=443
POSTGRES_PASSWORD=$database_password
JWT_SECRET=$jwt_secret
ENCRYPTION_KEY=$encryption_key
EOF
echo "Configuracao criada em $output (permissao 600). Nenhum segredo foi exibido."

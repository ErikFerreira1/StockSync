# Demo pública na AWS EC2

A demo foi instalada em uma `t4g.small` em Ohio (`us-east-2`), com Ubuntu Server 24.04 ARM64, 2 GiB de RAM e um volume gp3 de 16 GiB. O grupo de segurança permite SSH do IP de administração e HTTP/HTTPS para o site. O PostgreSQL e o Spring Boot usam a rede interna do Compose.

A conta usa o [plano gratuito da AWS](https://aws.amazon.com/pt/free/free-tier-faqs/), com duração de até seis meses ou até acabar o saldo de créditos. A VM usa um IPv4 público atribuído na inicialização. Se ela for parada e iniciada novamente, confira o IP e atualize a configuração e o link da demo se o endereço mudar.

## Instalação

Com Docker Engine e Compose instalados, copie o código por SSH, incluindo `compose.deploy.yml` e `deploy/`. Gere os segredos na VM. Substitua o endereço de exemplo pelo IPv4 público da instância.

```bash
./deploy/configure.sh https://203.0.113.10
```

Para o Caddy emitir e servir um certificado para o IP, a instalação na EC2 usa estas opções no começo de `deploy/Caddyfile`. Mantenha as rotas existentes dentro do bloco do site.

```caddyfile
{
	default_sni {$SITE_ADDRESS}
}

{$SITE_ADDRESS} {
	tls {
		issuer acme {
			profile shortlived
		}
	}
	# Rotas do site
}
```

O perfil `shortlived` é exigido para certificados de IP do Let's Encrypt. O Caddy renova o certificado usando o volume persistente de dados. [Certificados de IP](https://letsencrypt.org/2026/01/15/6day-and-ip-general-availability) · [Configuração do Caddy](https://caddyserver.com/docs/caddyfile/options#default-sni)

Na VM, limitei a memória do Maven com `MAVEN_OPTS=-Xmx512m` no comando `RUN` do Dockerfile do backend. Compile as duas imagens em sequência para reduzir o pico de uso de memória.

```bash
sudo docker compose --env-file .env.deploy -f compose.deploy.yml build app
sudo docker compose --env-file .env.deploy -f compose.deploy.yml build web
sudo docker compose --env-file .env.deploy -f compose.deploy.yml up -d --no-build
```

Confirme a abertura da interface, o login da conta demo e o fluxo de venda simulada. O teste em `tests/deploy_smoke.py` grava dados fictícios e verifica também a importação repetida, que deve preservar o saldo após a primeira baixa.

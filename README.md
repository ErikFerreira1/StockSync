# StockSync

[![StockSync CI](https://github.com/ErikFerreira1/StockSync/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/ErikFerreira1/StockSync/actions/workflows/ci.yml)

**Gestão de estoque com sincronização de anúncios no Mercado Livre.**

Projeto de portfólio backend em Java e Spring Boot que centraliza produtos, estoque e pedidos, atualiza anúncios vinculados e registra o resultado de cada sincronização. A versão atual oferece API REST, interface web em React, documentação interativa e um modo demo com marketplace simulado.

**Tecnologias principais:** Java 17 · Spring Boot · PostgreSQL · Spring Security/JWT · Docker · JUnit · Testcontainers · React · TypeScript

## O que você pode fazer na interface

| Tela | O que você pode fazer |
| --- | --- |
| Visão geral | Acompanhar indicadores de estoque e movimentações recentes. As notificações mostram estoque no limite, movimentações e falhas de sincronização. |
| Estoque e movimentações | Buscar por nome ou SKU, cadastrar e editar produtos, definir o estoque mínimo, ativar ou desativar produtos em lote e registrar entradas e saídas. O detalhe do produto mostra o saldo e o histórico de movimentações. |
| Canais de venda | Cadastrar, editar, ativar e desativar canais. No modo real, o administrador conecta a conta do Mercado Livre por OAuth. |
| Anúncios | Vincular um anúncio existente ou, no modo real, publicar um novo anúncio sem variações. O formulário consulta categorias e campos exigidos, valida os dados e mostra uma revisão antes de publicar. |
| Pedidos | Criar e revisar pedidos com vários itens, consultar detalhes, confirmar pagamentos já recebidos, cancelar no StockSync e buscar pedidos pagos no canal. Na demo, **Simular venda** gera um pedido para testar a importação. |
| Sincronizações | Consultar o histórico paginado de estoque, status de anúncios e pedidos. Filtrar por resultado, origem, canal, produto ou pedido e abrir pedidos a partir do histórico. |
| Mercado Livre simulado | Conferir o estoque publicado, preço e status dos anúncios simulados e acompanhar como eles mudam após uma movimentação ou venda. |

### Como funciona

A confirmação de pagamento de um pedido ou uma movimentação altera o estoque local. Criar um pedido pendente não baixa nem reserva estoque. Após a confirmação da transação no banco, eventos acionam a sincronização dos anúncios elegíveis e o resultado fica registrado para consulta. Desativar um produto pausa seus anúncios; reativá-lo recupera os anúncios pausados por esse fluxo com a quantidade atual.

Por exemplo: uma saída de duas unidades reduz o saldo de `12` para `10` e atualiza o anúncio vinculado para `10`. A integração atual sincroniza **estoque e status**, sem atualização automática de preços.

## Teste o projeto

### Demo online

**[Abrir a versão web do StockSync](https://18.227.102.237/)**

Entre com o usuário `demo` e a senha `stocksync-demo`. Siga o [roteiro pela interface](#demonstração-rápida-pela-interface) para ver como uma venda simulada altera o estoque e o anúncio vinculado.

A demo compartilhada roda na AWS EC2. Ela usa dados fictícios e simula o Mercado Livre para que você possa experimentar os fluxos sem conectar uma conta. Os registros de exemplo são restaurados na inicialização. Se a demo estiver offline, use a execução local abaixo.

O [Swagger da API](https://18.227.102.237/api/swagger-ui.html) fica disponível para quem quiser consultar os endpoints e fazer requisições com a mesma conta demo.

### Execução local

Com Docker e Docker Compose disponíveis, execute na pasta do projeto:

```bash
docker compose up --build
```

Abra o [Swagger UI](http://localhost:8080/swagger-ui.html), faça login em `POST /auth/login` com usuário `demo` e senha `stocksync-demo`, copie o `accessToken` e cole em **Authorize**.

A demonstração já prepara dois produtos e anúncios simulados. Consulte o catálogo, registre uma saída e acompanhe a sincronização — sem conta do Mercado Livre ou configuração de OAuth. O roteiro completo está na seção expansível **Modo demo** abaixo.

Também é possível importar a [collection do Postman](docs/postman/StockSync-Demo.postman_collection.json) e o [ambiente local](docs/postman/StockSync-Local.postman_environment.json). Selecione o ambiente no Postman e execute as requisições na ordem das pastas: o login salva o token automaticamente e a consulta de produtos salva o primeiro ID encontrado.

### Interface web

Com o backend em execução, utilize Node.js 22 e abra outro terminal:

```bash
cd frontend
npm ci
npm run dev
```

Abra [http://localhost:5173](http://localhost:5173) e entre com a conta demo. A interface oferece estoque, movimentações, canais, anúncios, pedidos e histórico de sincronizações. No perfil demo, também exibe o Mercado Livre simulado e a opção **Simular venda**.

O servidor Vite encaminha as requisições para a API em `http://localhost:8080`. Para usar outra porta, copie `frontend/.env.example` para `frontend/.env.local`, ajuste `VITE_BACKEND_URL` e reinicie o Vite. O Compose inicia apenas o backend e o banco; a interface é executada separadamente.

### Demonstração rápida pela interface

1. Abra **Estoque** e confira as 12 unidades iniciais do cabo USB-C em uma demo recém-iniciada.
2. Em **Pedidos → Simular venda**, escolha o canal simulado, o anúncio do cabo e 2 unidades.
3. Clique em **Gerar venda simulada → Ir para sincronização → Buscar pedidos no canal**.
4. Feche a consulta e abra o pedido importado: ele estará como **Pago**, com 2 unidades e total de R$ 50,00.
5. Confira o saldo de 10 unidades em **Estoque** e **Mercado Livre simulado** e consulte os registros em **Sincronizações**.
6. Repita a sincronização: o pedido e a baixa não são duplicados.

### Escopo do projeto

O foco é demonstrar regras de estoque, integração externa, transações, concorrência e testes em um projeto de portfólio backend, com uma interface para experimentar os fluxos. A integração real disponível é com o Mercado Livre. Reembolsos, pagamentos processados pelo sistema e outros marketplaces ficam fora do escopo atual.

---

## Detalhes técnicos e guias

Expanda apenas o assunto que deseja explorar.

<details>
<summary>Regras de negócio e permissões</summary>

### Gestão de produtos e estoque

- Cadastro e edição de produtos com SKU, nome, descrição e preço-base.
- Consulta de produtos e das respectivas quantidades disponíveis.
- Controle de estoque mínimo por produto e consulta dos itens abaixo desse limite.
- Registro de entradas e saídas manuais, com quantidade, origem, data e observação.
- Ativação e desativação de produtos.
- Histórico de movimentações para acompanhar como o saldo de cada produto foi alterado.

### Canais de venda e anúncios

- Cadastro, edição, ativação e desativação de canais de venda.
- Vínculo entre um produto local e um anúncio do marketplace, utilizando o identificador externo do anúncio.
- Publicação de anúncios sem variações no Mercado Livre, com consulta de categoria, validação e revisão antes de publicar. Veja o [guia de publicação](docs/MARKETPLACE_PUBLICATION.md).
- Sincronização de quantidade para os anúncios ativos elegíveis após a confirmação da alteração no banco.
- Pausa dos anúncios ativos vinculados a canais ativos quando o produto é desativado.
- Reativação dos anúncios pausados pela desativação do produto, enviando a quantidade local atual.

A reativação considera a origem da pausa: anúncios que já estavam pausados por outro motivo não são reativados automaticamente por esse fluxo.

O preço-base é um atributo do produto local. A sincronização implementada contempla **estoque e status**; alterar o preço-base não atualiza automaticamente o preço no Mercado Livre. Cadastrar um vínculo de anúncio também não equivale à publicação de um novo anúncio no marketplace.

### Pedidos e movimentações

- Registro e consulta de pedidos com seus itens e quantidades. Novos pedidos ficam como `PENDING`, sem baixar nem reservar estoque.
- Em **Pedidos → detalhes → Confirmar pagamento**, ADMIN e OPERATOR podem registrar um pagamento já recebido. A ação verifica todos os itens, baixa o estoque e marca o pedido como `PAID` na mesma transação. Saldo insuficiente mantém o pedido pendente, sem baixas parciais; repetir a confirmação não desconta novamente.
- Confirmação disponível também pela API em `PATCH /orders/{id}/confirm-payment`. Ela registra o pagamento localmente, sem efetuar cobrança ou alterar pagamentos no Mercado Livre.
- Cancelamento de pedidos pendentes ou pagos, com reposição somente quando houver uma baixa ativa. Cancelar um pendente novo não altera o estoque; repetir o cancelamento não devolve quantidades novamente. A ação não cancela a venda no Mercado Livre nem emite reembolso.
- Importação de pedidos pagos do Mercado Livre em **Pedidos → Sincronizar pedidos** ou pela API, com criação e confirmação de pagamento na mesma transação. Falta de estoque desfaz a importação daquele pedido.
- Compatibilidade com pedidos antigos: a migração V12 identifica as baixas existentes pelo histórico de movimentações, preservando saldos e status. Confirmar um pendente antigo que já teve baixa não desconta o estoque novamente.
- Proteções contra importação duplicada e cenários de concorrência, verificadas por testes de integração.

Veja os detalhes da interface e do contrato em [Pedidos na interface web](docs/ORDERS_WEB.md).

### Integração e rastreabilidade

- Autorização de contas do Mercado Livre pelo fluxo OAuth.
- Renovação de tokens de acesso e armazenamento criptografado das credenciais de integração.
- Registro de eventos de sincronização com sucesso ou falha.
- Consulta paginada do histórico com filtros por status, produto, canal e pedido. Veja o [guia de sincronizações](docs/SYNC_EVENTS_WEB.md).

### Autenticação e permissões

O acesso à API utiliza tokens JWT e três perfis de usuário:

| Perfil | Acesso |
| --- | --- |
| `ADMIN` | Operações administrativas, incluindo gestão de usuários e credenciais de integração. |
| `OPERATOR` | Operações de produtos, estoque, pedidos e canais, sem acesso à administração de usuários e credenciais. |
| `VIEWER` | Consultas dos recursos operacionais, sem permissão para alterá-los. |

O login possui limitação de tentativas por IP em cada instância da aplicação. No modo demo, a conta disponibilizada é do tipo `OPERATOR`, e a troca de sua senha pela API é bloqueada.

</details>

<details>
<summary>Modo demo — instalação, roteiro de testes e limites</summary>

O modo demo permite conhecer o fluxo de gestão e sincronização sem configurar uma conta do Mercado Livre, autorizar uma integração ou utilizar ngrok. Ele utiliza PostgreSQL e um adaptador que simula o estado dos anúncios externos, mantendo os mesmos serviços e eventos responsáveis pela sincronização.

### Pré-requisitos e inicialização

É necessário ter Docker e Docker Compose disponíveis. O Java, o Maven e o PostgreSQL utilizados pela aplicação são executados nos containers.

Na pasta do projeto, execute:

```bash
docker compose up --build
```

Na primeira execução, o Docker baixa as imagens e dependências, compila o projeto, inicia o banco e aplica as migrações. Aguarde a aplicação concluir a inicialização.

O Compose utiliza o perfil `demo` por padrão. Em uma cópia nova do projeto, não é necessário criar um arquivo `.env` para experimentar. Caso já exista um `.env`, seus valores podem substituir as configurações padrão. O arquivo [.env.example](.env.example) apresenta as variáveis disponíveis.

As portas padrão são `8080` para a API e `5432` para o PostgreSQL. Se já estiverem ocupadas, configure `APP_PORT` e `POSTGRES_PORT`. Utilize um banco dedicado à demonstração: os registros de exemplo são restaurados na inicialização do perfil `demo`.

### Acesso pelo Swagger

Abra [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html). Se alterou `APP_PORT`, ajuste a porta no endereço.

O Swagger apresenta os endpoints e permite enviar requisições pelo navegador. Para autenticar:

1. Abra `POST /auth/login` e clique em **Try it out**.
2. Informe o corpo abaixo e clique em **Execute**.
3. Copie o campo `accessToken` da resposta.
4. Clique em **Authorize**, cole somente o token e confirme.

```json
{
  "username": "demo",
  "password": "stocksync-demo"
}
```

Essas são as credenciais padrão. Elas podem ser alteradas pelas variáveis `DEMO_USERNAME` e `DEMO_PASSWORD` antes da inicialização.

### Dados de exemplo

A aplicação prepara uma conta demo, um canal de venda simulado e dois produtos com anúncios vinculados:

| Produto | SKU | Estoque inicial | Estoque mínimo | Preço-base |
| --- | --- | --- | --- | --- |
| Cabo USB-C reforçado | `DEMO-USB-C` | 12 | 3 | R$ 25,00 |
| Teclado mecânico compacto | `DEMO-KEYBOARD` | 2 | 5 | R$ 149,90 |

O teclado começa abaixo do estoque mínimo para demonstrar a consulta de itens que precisam de reposição.

### Roteiro de demonstração

1. Consulte `GET /products` e anote o ID do cabo USB-C.
2. Consulte `GET /inventory/below-minimum` para visualizar o teclado abaixo do limite.
3. Consulte `GET /demo/marketplace-listings` para observar o estoque e o status dos anúncios simulados.
4. Registre uma saída manual com `POST /stock-movement`, utilizando o ID do cabo no campo `productId`:

   ```json
   {
     "productId": 1,
     "quantity": 1,
     "type": "MANUAL_DECREASE",
     "note": "Teste de sincronização no modo demo"
   }
   ```

5. Consulte novamente o produto e os anúncios simulados. Partindo dos dados iniciais, a quantidade do cabo passa de `12` para `11` nos dois lados.
6. Execute `PATCH /products/{id}/deactivate`. O anúncio correspondente passa para `PAUSED`.
7. Execute `PATCH /products/{id}/activate`. O anúncio retorna para `ACTIVE` com o estoque local atual.
8. Consulte `GET /stock-movement/product/{productId}` para ver a movimentação e `GET /sync-events/product/{productId}` para acompanhar os resultados das sincronizações.

Em um banco novo, os produtos normalmente recebem os IDs `1` e `2`. Utilize sempre os IDs retornados pela API, pois eles podem variar. Para demonstrar uma entrada de estoque, registre uma movimentação com `type` igual a `MANUAL_INCREASE` e uma quantidade positiva.

### Comportamento e limites da simulação

- Os anúncios simulados ficam em memória; produtos, pedidos, movimentações e eventos são armazenados no PostgreSQL.
- A simulação não envia requisições nem altera anúncios reais no Mercado Livre.
- Para simular uma venda na interface, acesse **Pedidos → Simular venda**, selecione o canal, anúncio e quantidade e clique em **Gerar venda simulada**. Em seguida, use **Ir para sincronização → Buscar pedidos no canal**. O pedido entra como `PAID`, com baixa de estoque e sincronização dos anúncios simulados.
- Pela API, consulte `GET /demo/orders/listings?salesChannelId={id}` e envie `POST /demo/orders` com `salesChannelId`, `listingId` e `quantity`. Depois execute `POST /sales-channels/{id}/orders/synchronize`. A criação da venda simulada não altera o estoque local; a importação faz a baixa. Reimportar o mesmo pedido não duplica a venda nem a baixa.
- As rotas de simulação existem somente no perfil `demo`; ADMIN e OPERATOR podem gerar vendas. Os pedidos externos simulados ficam em memória e são perdidos ao reiniciar; pedidos já importados permanecem no banco. Falhas de importação ficam disponíveis para nova tentativa enquanto o simulador estiver em execução.
- Os dois anúncios de exemplo são registrados na inicialização. Novos vínculos locais também são registrados no simulador após a confirmação da transação.
- Ao reiniciar a aplicação, a conta demo e os dados dos produtos e anúncios de exemplo são restaurados. Isso não apaga todo o histórico nem os demais registros criados durante os testes.
- As rotas de usuários, credenciais e OAuth não aparecem na documentação do perfil demo; a conta demo não possui acesso administrativo.

### Parar ou reiniciar a demonstração

Para parar os containers mantendo o banco:

```bash
docker compose down
```

Para apagar completamente o banco da demonstração e começar novamente:

```bash
docker compose down -v
docker compose up --build
```

> A opção `-v` remove permanentemente o volume do banco associado ao projeto Compose. Use esse comando apenas no ambiente de demonstração que deseja apagar.

</details>

<details>
<summary>Arquitetura e organização do código</summary>

A aplicação separa a entrada HTTP, as regras de negócio, a persistência e a comunicação com o marketplace. Os serviços dependem de uma interface de integração, e o perfil ativo seleciona a implementação real ou simulada.

```mermaid
flowchart TD
    API[API REST] --> Services[Serviços de negócio]
    Services --> DB[(PostgreSQL)]
    Services --> Events[Eventos transacionais]
    Events --> Sync[Serviços de sincronização]
    Sync --> Port[Interface de integração]
    Port --> Demo[Adaptador simulado]
    Port --> ML[Adaptador Mercado Livre]
    Sync --> Audit[Histórico de sincronização]
    Audit --> DB
```

No fluxo de estoque e status, a sincronização é acionada após a confirmação da transação local. Seus resultados são registrados para consulta. Uma falha na comunicação externa pode deixar o anúncio temporariamente diferente do estado local, e o histórico permite identificar essa situação.

A interface de integração concentra os detalhes de comunicação no adaptador de cada marketplace. O suporte a novas plataformas ainda exige implementar seus adaptadores e regras específicas.

### Organização do código

Os pacotes abaixo estão em `src/main/java/com/erikferreira/stocksync`:

| Pacote | Responsabilidade |
| --- | --- |
| `controller` | Endpoints da API REST. |
| `service` | Regras de negócio e operações da aplicação. |
| `entity` | Entidades persistidas e seus relacionamentos. |
| `repository` | Acesso aos dados com Spring Data JPA. |
| `dto` | Estruturas de entrada e saída da API. |
| `integration` | Contratos e adaptadores de integração externa. |
| `listener` | Tratamento dos eventos que acionam a sincronização. |
| `config` | Configurações, segurança e inicialização dos dados demo. |

As migrações Flyway ficam em `src/main/resources/db/migration`, e os testes em `src/test`.

</details>

<details>
<summary>Tecnologias e suas responsabilidades</summary>

| Tecnologia | Utilização |
| --- | --- |
| Java 17 e Spring Boot 3.5 | Desenvolvimento e execução do backend. |
| Spring Web e Bean Validation | API REST e validação das requisições. |
| Spring Security e JWT | Autenticação e autorização. |
| Spring Data JPA e Hibernate | Persistência e mapeamento das entidades. |
| PostgreSQL 17 | Banco de dados relacional. |
| Flyway | Evolução versionada do esquema do banco. |
| Springdoc OpenAPI e Swagger UI | Documentação interativa da API. |
| Docker e Docker Compose | Execução da aplicação e do banco em containers. |
| Maven e Maven Wrapper | Gerenciamento de dependências e compilação. |
| JUnit 5 e Mockito | Testes automatizados e simulação de dependências. |
| Testcontainers | Testes de integração com PostgreSQL em containers. |
| JaCoCo | Relatório de cobertura dos testes. |
| React, TypeScript e Vite | Interface web e servidor de desenvolvimento com proxy para a API. |
| Vitest e Testing Library | Testes dos componentes e contratos HTTP do frontend. |

</details>

<details>
<summary>Testes automatizados e cobertura</summary>

Para executar os testes fora do container da aplicação, é necessário ter um JDK compatível com Java 17 e Docker disponível. O Maven Wrapper está incluído no repositório.

```bash
./mvnw test
```

Os testes abrangem regras de estoque e pedidos, permissões, integração com o banco, sincronização, duplicidade, concorrência e o modo demo. O Testcontainers prepara bancos temporários para os testes de integração.

Para executar a verificação e gerar o relatório de cobertura:

```bash
./mvnw verify
```

O relatório do JaCoCo fica em `target/site/jacoco/index.html`. No Windows, utilize `mvnw.cmd` no lugar de `./mvnw`.

### Testes da interface web

Na pasta `frontend`, com as dependências instaladas:

```bash
npm test
npm run build
```

Os testes ficam em `frontend/tests` e utilizam APIs simuladas. O build verifica TypeScript e gera os arquivos em `frontend/dist`.

### Integração contínua com GitHub Actions

O workflow [StockSync CI](.github/workflows/ci.yml) executa a cada push e em pull requests destinados à `main`. Também pode ser iniciado manualmente pela aba **Actions** do repositório.

O pipeline executa dois jobs independentes: o backend prepara Java 17, executa `verify`, disponibiliza os relatórios de testes e cobertura por sete dias e verifica a construção da imagem Docker; o frontend prepara Node.js 22, instala as dependências com `npm ci`, executa `npm test` e verifica os tipos e o build com `npm run build`. Os testes de integração utilizam bancos temporários criados pelo Testcontainers. O workflow não publica imagens nem realiza deploy.

O badge no início deste README mostra o resultado do CI na branch `main` após a execução no GitHub.

</details>

<details>
<summary>Configuração da integração real com o Mercado Livre</summary>

O modo real exige uma aplicação configurada no Mercado Livre, as respectivas credenciais e a autorização da conta que será conectada.

Uma **conta de teste do Mercado Livre** também utiliza esse fluxo real: a autorização OAuth deve ser feita com o vendedor de teste em um canal separado. Isso é diferente do perfil `demo`, que usa o simulador local e não publica na plataforma. Confira a identidade da conta autorizada antes de publicar; uma sessão já aberta no Mercado Livre pode reutilizar a conta principal. A criação do anúncio não garante ativação imediata: ele pode retornar pausado enquanto a plataforma processa as fotos.

1. Crie um arquivo `.env` com base em [.env.example](.env.example).
2. Configure `SPRING_PROFILES_ACTIVE=default` para utilizar o adaptador real. No Compose, deixar essa variável ausente ou vazia seleciona o perfil `demo`.
3. Configure o acesso ao PostgreSQL, as credenciais iniciais do administrador, `JWT_SECRET`, `ENCRYPTION_KEY` e `MERCADO_LIVRE_REDIRECT_URI`.
4. Com uma conta administrativa, cadastre o canal em **Canais de venda** e abra **Conectar Mercado Livre** para informar as credenciais do aplicativo.
5. Conclua a autorização no navegador. Pela API, o fluxo pode ser iniciado em `GET /mercadolivre/authorize/{salesChannelId}` com autenticação administrativa e precisa preservar o cookie de estado OAuth.

O endereço de retorno deve corresponder ao callback configurado na aplicação do Mercado Livre e encaminhar para `/mercadolivre/callback`. Cada conta conectada precisa autorizar o acesso.

Para executar o backend pela IDE, inicie apenas o banco:

```bash
docker compose up -d postgres
```

Configure também `POSTGRES_URL`, por exemplo `jdbc:postgresql://localhost:5432/stocksync`, ajustando a porta e o nome do banco. Essa variável é definida automaticamente para o serviço `app` no Compose, mas precisa estar disponível ao executar pela IDE ou pelo Maven.

Inicie a classe `StockSyncApplication` ou execute:

```bash
./mvnw spring-boot:run
```

As chaves e senhas padrão do Compose são destinadas à demonstração local. Substitua-as antes de hospedar a aplicação ou conectar contas reais. Não versione `.env`, tokens ou segredos de integração.

</details>


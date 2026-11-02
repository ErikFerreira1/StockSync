# StockSync — handoff técnico para continuidade da interface web

Estado conferido em 2 de novembro de 2026, branch `main`, commit `afb26e3` (`origin/main`). Este documento descreve o repositório como está, incluindo o frontend React já integrado ao mesmo repositório do backend.

## 1. Visão geral

### Projeto

**StockSync** é uma aplicação para gestão de estoque e sincronização de anúncios vinculados ao Mercado Livre. A interface web oferece uma camada operacional simples para consultar produtos, alterar dados e saldos, registrar movimentos e acompanhar alertas. O backend também mantém pedidos, canais de venda, anúncios, autenticação, integração OAuth e rastreabilidade das sincronizações.

Repositório: `https://github.com/ErikFerreira1/StockSync`.

### Stack

| Área | Tecnologias |
|---|---|
| Linguagem backend | Java 17 |
| Backend | Spring Boot 3.5.0, Spring MVC/Web, Spring Data JPA, Hibernate, Spring Security, OAuth2 Client e Resource Server, Bean Validation |
| Segurança | JWT stateless, perfis `ADMIN`, `OPERATOR`, `VIEWER`, BCrypt/PasswordEncoder delegating, controle de tentativas de login por IP |
| Persistência | PostgreSQL 17 (`postgres:17-alpine` no Compose), Flyway, Hibernate com `ddl-auto=validate` |
| Frontend | React 18.3.1, TypeScript ~5.6.3, Vite 6, `lucide-react` 0.468 |
| CSS | CSS próprio em `frontend/src/styles.css`; não há framework de UI |
| Integração externa | Mercado Livre OAuth/API por Spring `RestClient`; adapter de demonstração local no perfil `demo` |
| API docs | Springdoc OpenAPI 2.8.9, Swagger UI |
| Testes | JUnit 5, Mockito, Spring Security Test, Testcontainers PostgreSQL, JaCoCo 0.8.14 |
| Build/deploy local | Maven Wrapper, Docker, Docker Compose |

## 2. Arquitetura e estrutura

### Repositório

O projeto é um monorepo: backend na raiz e aplicação React dentro de `frontend/`.

```text
.
├── src/main/java/com/erikferreira/stocksync/
│   ├── config/                 # Spring, bootstraps, OpenAPI e segurança
│   ├── controller/             # REST controllers e handler de exceções
│   ├── dto/                     # contratos de entrada/saída por domínio
│   ├── entity/                  # entidades JPA e enums
│   ├── event/                   # eventos internos de mudança de estoque/status
│   ├── integration/             # ports, DTOs e adapters de marketplace
│   ├── listener/                # listeners dos eventos internos
│   ├── repository/              # Spring Data JPA
│   └── service/                 # regras de negócio e processors
├── src/main/resources/
│   ├── application*.properties
│   └── db/migration/            # migrations Flyway V1–V10
├── src/test/java/               # testes unitários, contrato e integração
├── frontend/
│   ├── src/App.tsx               # shell, estado e telas da SPA
│   ├── src/api.ts                # cliente HTTP, JWT e funções de API
│   ├── src/types.ts              # tipos do contrato usado pelo front
│   ├── src/styles.css             # design e responsividade
│   ├── src/main.tsx              # bootstrap React
│   ├── vite.config.ts             # dev server/proxy `/api` -> backend
│   └── package.json
├── docker-compose.yml
├── Dockerfile
├── pom.xml
└── docs/postman/                 # collection demo e environment local
```

### Comunicação entre camadas

1. O navegador executa a SPA Vite/React. No desenvolvimento, `frontend/vite.config.ts` encaminha `/api/*` para `http://localhost:8080/*` e remove o prefixo `/api`. O `VITE_API_URL` pode substituir o prefixo consumido pelo cliente HTTP.
2. `frontend/src/api.ts` inclui `Authorization: Bearer <JWT>` quando há token em `localStorage` (`stocksync.accessToken`). Respostas 401 limpam o token e emitem `stocksync:unauthorized` para a SPA voltar ao login. Erros HTTP viram `ApiError` com status e mensagem do backend.
3. Controllers REST validam DTOs e chamam services. Services implementam regras transacionais, repositories persistem via JPA/PostgreSQL.
4. Alterações de estoque/status disparam eventos de domínio. Listeners/serviços de sincronização atualizam anúncios vinculados e registram `SyncEvent`. O adapter é selecionado pelo profile: `demo` usa marketplace simulado; profile não-demo usa Mercado Livre real.
5. O PostgreSQL é inicializado pelo Compose; Flyway aplica migrations antes de Hibernate validar o esquema.

### Padrões e convenções existentes

- Backend em pacotes por papel/domínio: controller, DTO, entity, repository, service, integration, listener, event.
- DTOs separam API das entidades JPA; controllers usam `@Valid` e paginação Spring (`Page<T>`, query params `page`, `size`, `sort`).
- Serviços concentram validação de regra e transações; concorrência de estoque/pedidos usa locking pessimista em fluxos críticos.
- Integração é abstraída por ports e adapters (`MarketplaceIntegrationPort`, `ErpStockPort`), com eventos para desacoplar gravação de sincronização.
- API REST sem prefixo global `/api`; `/api` é convenção local do proxy Vite. Swagger está na raiz da API.
- Front ainda concentra boa parte do estado e apresentação em `App.tsx`; funções de fetch ficam em `api.ts`, tipos em `types.ts`, estilos em CSS global.
- Convenção de commits usada na branch: Conventional Commits (`feat:`, `fix:`, `style:`). O usuário pediu commits separados por implementação e que se preserve o padrão existente.

## 3. O que já está pronto

### Funcionalidades do produto

#### Backend

- Cadastro, consulta paginada, edição, ativação/desativação de produtos.
- Inventário por produto, saldo disponível, estoque mínimo e listagem abaixo do mínimo.
- Movimentação manual de entrada e saída com nota, tipo/origem, data e produto associado.
- Agora, alterar o saldo pelo formulário de edição também grava movimentação manual com a diferença líquida. Se o saldo não mudou, não cria movimento. Inventário e histórico são gravados na mesma transação.
- Pedidos com itens, baixa de estoque ao registrar venda e reposição ao cancelar.
- Sincronização/importação de pedidos por canal e proteção contra duplicidade/concorrência.
- Canais de venda e vínculos de anúncios.
- Sincronização de saldo após movimento/pedido; desativar produto pausa anúncios ativos elegíveis. Reativar só retoma anúncios pausados pelo fluxo de desativação do produto, preservando pausas externas.
- OAuth Mercado Livre no profile real, renovação de tokens e credenciais criptografadas.
- Eventos de sincronização com status, tentativas e mensagem de falha.
- Usuários e papéis, JWT, bootstrap de administrador e usuário demo, rate limit de login.
- Profile `demo` com dois produtos e anúncios simulados, sem conta Mercado Livre.

#### Frontend web (estado atual)

- Login JWT com estado de erro distinguindo credenciais inválidas, rate limit e API indisponível.
- Shell visual com menu lateral: Visão geral, Estoque, Movimentações, Sincronizações; breadcrumb, indicador de API, sino de notificações e logout.
- Visão geral com contagens de produtos ativos, unidades em estoque, itens abaixo do mínimo e falhas de sync; lista de baixo estoque e movimentos recentes.
- Estoque com filtro textual por nome/SKU, tabela de saldo, mínimo, preço, status e ações; seleção individual e selecionar todos os resultados visíveis; botões de ativação/desativação em lote desabilitados quando nada está selecionado.
- Detalhe de produto em rota URL `/stock/:id` (ex.: `localhost:5173/stock/8`), acessado clicando no nome. Exibe SKU, saldo, mínimo, preço, status, edição, ajuste de estoque e histórico específico.
- Edição de produto: nome, SKU, descrição, preço base, saldo disponível e estoque mínimo. Nome/SKU/preço são enviados a `PUT /products/{id}`; mínimo e saldo usam endpoints específicos de inventory.
- Modal de movimentação a partir do Estoque e formulário de entrada/saída no detalhe, com quantidade, saldo resultante, motivo/observação; movimento no detalhe está aplicado ao produto aberto.
- Histórico de Movimentações identifica produto/nome/SKU, tipo, quantidade, data e observação.
- Tela de Sincronizações mostrando falhas recentes, produto, status, data, tentativas e erro.
- Sino no topo com notificações client-side derivadas de estoque baixo, falhas de sync e movimentos carregados; bolinha vermelha para não lidas; abrir marca as atuais como vistas, botão limpar dispensa, lista com limite visual/scroll. Estado visto/dispensado em localStorage.
- Toast temporário no canto inferior direito para resultado/erro de ações.
- Botão único “Atualizar dados” no canto superior direito compartilhado pelas telas. O botão duplicado no painel inicial foi removido.
- Fonte maior e tratamento visual baseado na exploração Technine Inventory Management, simplificado para contexto de portfólio; CSS próprio, tons verdes/teal e sidebar.

### Rotas REST atuais

Não existe context path `/api` no servidor; abaixo estão caminhos relativos à raiz `http://localhost:8080`. Exceto login/callback/docs descritos, é necessário JWT. As permissões globais estão resumidas após a tabela.

| Método | Caminho | Descrição |
|---|---|---|
| `POST` | `/auth/login` | Autentica `{username,password}` e retorna access token JWT, tipo, expiração e usuário. Público; rate limit por IP. |
| `GET` | `/products?page=&size=&sort=` | Lista produtos paginados. |
| `GET` | `/products/{id}` | Consulta produto. |
| `POST` | `/products` | Cria produto. |
| `PUT` | `/products/{id}` | Atualiza SKU, nome, descrição e preço base. |
| `PATCH` | `/products/{id}/deactivate` | Desativa produto e provoca fluxo de pausa elegível de anúncios. |
| `PATCH` | `/products/{id}/activate` | Ativa produto e tenta reativar anúncios pausados pela desativação. |
| `GET` | `/inventory/{productId}` | Consulta inventário e saldo/mínimo. |
| `GET` | `/inventory/below-minimum` | Lista produtos abaixo do mínimo. |
| `PATCH` | `/inventory/product/{productId}/min-quantity` | Atualiza mínimo; corpo `{ "minQuantity": n }`. |
| `PATCH` | `/inventory/product/{productId}/quantity` | Define saldo absoluto; corpo atual do front `{ "productId": id, "quantity": n }`; cria histórico de entrada/saída conforme delta. |
| `GET` | `/stock-movement/product/{productId}?page=&size=&sort=` | Lista histórico de movimentos paginado por produto. |
| `POST` | `/stock-movement` | Registra entrada/saída manual. Contrato contém `productId`, `quantity`, `type` (`MANUAL_INCREASE`/`MANUAL_DECREASE`) e `note`. |
| `GET` | `/sync-events/product/{productId}?page=&size=&sort=` | Histórico de sync de um produto. |
| `GET` | `/sync-events/failed?page=&size=&sort=` | Lista eventos de sync falhos. |
| `GET` | `/sales-channels?page=&size=&sort=` | Lista canais. |
| `GET` | `/sales-channels/{id}` | Consulta canal. |
| `POST` | `/sales-channels` | Cria canal. |
| `PUT` | `/sales-channels/{id}` | Atualiza canal. |
| `PATCH` | `/sales-channels/{id}/activate` | Ativa canal. |
| `PATCH` | `/sales-channels/{id}/deactivate` | Desativa canal. |
| `GET` | `/marketplace-listings?page=&size=&sort=` | Lista vínculos de anúncios. |
| `GET` | `/marketplace-listings/{id}` | Consulta vínculo. |
| `POST` | `/marketplace-listings` | Cria vínculo entre produto/canal e ID externo do anúncio. Não publica anúncio novo no marketplace. |
| `GET` | `/orders?page=&size=&sort=` | Lista pedidos. |
| `GET` | `/orders/{id}` | Consulta pedido e itens. |
| `POST` | `/orders` | Registra pedido; processa itens e estoque. |
| `PATCH` | `/orders/{id}/cancel` | Cancela pedido e repõe estoque de acordo com regra do serviço. |
| `POST` | `/sales-channels/{salesChannelId}/orders/synchronize` | Aciona importação/sincronização de pedidos do canal; retorna 204. |
| `GET` | `/users?page=&size=&sort=` | Lista usuários; somente ADMIN. |
| `GET` | `/users/{id}` | Consulta usuário; somente ADMIN. |
| `POST` | `/users` | Cria usuário; somente ADMIN. |
| `PATCH` | `/users/{id}/activate` | Ativa usuário; somente ADMIN. |
| `PATCH` | `/users/{id}/deactivate` | Desativa usuário; somente ADMIN. |
| `PATCH` | `/users/me/password` | Altera senha autenticada fora do modo demo; bloqueado no demo. A rota está coberta pela regra ADMIN de `/users/**` antes da regra específica em `SecurityConfig`, então confira a configuração se ajustar o comportamento esperado para todos os papéis. |
| `GET` | `/integration-credentials/{id}` | Consulta credencial por ID; somente ADMIN. |
| `GET` | `/integration-credentials/sales-channel/{salesChannelId}` | Consulta credencial por canal; somente ADMIN. |
| `POST` | `/integration-credentials` | Registra credencial de integração; somente ADMIN. |
| `GET` | `/mercadolivre/authorize/{salesChannelId}` | Inicia OAuth e redireciona ao Mercado Livre; somente ADMIN e profile real (`!demo`). |
| `GET` | `/mercadolivre/callback?code=&state=` | Callback OAuth, valida state/cookie e persiste tokens; público para receber redirecionamento. Profile real. |
| `GET` | `/demo/marketplace-listings` | Lista estado de anúncios simulados; apenas profile `demo`. |
| `GET` | `/demo/marketplace-listings/{listingId}` | Inspeciona anúncio simulado; apenas profile `demo`. |
| `GET` | `/swagger-ui.html`, `/v3/api-docs/**` | Documentação OpenAPI; público no profile demo. |

**Segurança global:** `OPTIONS` e login são públicos. `GET` exige qualquer um de `ADMIN`, `OPERATOR`, `VIEWER`; demais métodos exigem ADMIN ou OPERATOR. `/users/**`, credenciais e início de OAuth restringem ADMIN. Em demo, Swagger é público e mudança de senha é bloqueada. JWT vai no header `Authorization: Bearer ...`.

### Entidades e relações

| Entidade | Campos/relacionamentos principais |
|---|---|
| `Product` | SKU único, nome, descrição, preço base, ativo. 1:1 com `Inventory`; 1:N com `StockMovement` e `MarketplaceListing`. |
| `Inventory` | Produto 1:1 obrigatório, saldo disponível, mínimo e timestamp de atualização. |
| `StockMovement` | N:1 produto, instante, quantidade, tipo, origem (`OriginType`/ID) e nota. Tipos incluem entrada e saída manuais; origens também cobrem fluxos de pedido/sistema. |
| `SalesChannel` | Nome, tipo, URL base, ativo. 1:1 credencial; 1:N listings e orders. |
| `MarketplaceListing` | N:1 produto e canal, ID/url externa, status, timestamp de sync e `pausedByProductDeactivation`. |
| `IntegrationCredential` | 1:1 canal; client ID, segredo criptografado, access/refresh token, expiração. Segredo é protegido por conversor de atributo. |
| `Order` | N:1 canal, ID externo, data, status; 1:N itens. |
| `OrderItem` | N:1 pedido e produto, quantidade e preço unitário. |
| `SyncEvent` | N:1 canal obrigatório; opcionalmente produto e pedido; ID pedido externo, timestamp, status, erro, tentativas. |
| `User` | Usuário, hash de senha, role, ativo, `authVersion` para invalidar tokens e timestamp de criação. |

Enums de domínio em `entity/enums`: `ChannelType`, `ListingStatus`, `MovementType`, `OriginType`, `OrderStatus`, `SyncStatus`, `UserRole`.

### Telas/componentes React

- `App.tsx`: roteamento manual baseado em `window.location`/history, autenticação, carregamento/refresh de dados, notificações, dialogs e páginas.
- Telas: Login; Visão geral; Estoque; detalhe do produto (`/stock/:id`); Movimentações; Sincronizações.
- Subcomponentes locais: `Stat`, `ProductLine`, toast, modal de edição, modal de movimentação, popover de notificações.
- `api.ts`: funções `login`, produtos, inventário, histórico, eventos de sync e movimento manual.
- `types.ts`: `User`, `LoginResponse`, `Product`, `Page<T>`, `Inventory`, `StockMovement`, `SyncEvent`.
- Não existe tela frontend para pedidos, canais, anúncios/configuração OAuth, gerenciamento de usuários ou edição de configurações ainda; esses recursos são acessíveis pela API/Swagger.

## 4. Decisões técnicas tomadas

| Decisão | Motivo/contexto | Alternativas descartadas ou limite |
|---|---|---|
| React + TypeScript no front | Foi a stack escolhida para demonstrar tecnologias comuns em vagas Java e permitir SPA tipada. | Não foi adotado Angular/Vue; manter React/TS em incrementos. |
| Front dentro do mesmo repositório em `frontend/` | Preferência explícita do usuário por monorepo com separação clara. | Não criar repo separado nem misturar fontes TS no diretório Java. |
| Começar com interface pequena e expandir | O usuário quis iniciar com login, painel/estoque/produtos/movimentações/sincronização em etapas; funcionalidades mais amplas ficam para depois. | Não construir todas as áreas operacionais e administração de uma vez. |
| Design simplificado inspirado em Technine Inventory Management | O usuário gostou da direção visual, mas pediu uma interface simples, fácil de usar e sem estética genérica de IA, adequada ao portfólio. | Evitar dashboard futurista/gradientes decorativos e excesso de elementos; CSS autoral simples. |
| Um botão global de atualização no canto superior direito | O usuário viu dois botões e pediu manter apenas o superior direito em todas as telas. Commit `afb26e3` remove o do painel de atividade. | Não reintroduzir refresh independente no conteúdo da tela sem pedido explícito. |
| Página de detalhe dedicada por produto | Ajuste de saldo é operação individual; fluxo não deve ajustar todos em lote. URL implementada como `/stock/{id}`; nome do produto é link clicável. | Não usar ação “abrir produto” redundante na tabela; ações em lote ficam só para ativar/desativar. |
| Ativação/desativação em lote no estoque | É uma operação coerente sobre seleção de produtos; botão desativar/ativar desabilita quando seleção vazia. | Ajuste de quantidade em lote foi descartado por não fazer sentido de negócio. |
| Edição total no formulário do produto | Pedido explícito: permitir editar nome, SKU, preço, quantidade e mínimo na aba; front combina update de produto e patches de inventory. | Quantidade não é apenas escrita silenciosa: deve gerar registro de movimento. |
| Mudança de saldo no editar cria movimento | Evita lacuna de auditoria: antes, endpoint atualizava `Inventory` sem persistir `StockMovement`. `InventoryService.adjustStockWithHistory` calcula delta, classifica entrada/saída e salva movimento no mesmo contexto transacional; delta zero não cria registro. | Não criar dois fluxos inconsistentes entre modal “Movimentar” e formulário “Editar”. |
| Histórico mostra nome e SKU do produto | Usuário apontou que entrada/saída sem identificação do produto era ambígua; tabela usa associação do ID do produto. | Não mostrar somente quantidade e tipo. |
| Persistência de notificações vistas/dispensadas no localStorage | A UI precisa manter a distinção entre itens não lidos, vistos e dispensados após navegação/refresh local. | Sem infraestrutura de push/websocket no backend; eventos são inferidos dos dados carregados, não entregues em tempo real. |
| JWT guardado em localStorage e API centralizada | Solução compatível com protótipo de portfólio e endpoints stateless já existentes. | Sem sessão server-side; lembrar que é uma decisão simples de SPA, não substitui hardening de produção. |
| `/api` apenas no proxy de desenvolvimento Vite | Simplifica CORS durante desenvolvimento sem mudar rotas já públicas do backend. | Em fetch, API continua com caminhos `/products`, `/auth/login`, etc. |
| Backend com profile demo e adapter fake | Permite demonstrar fluxo completo sem credenciais de marketplace e sem chamar serviço real. | Não misturar estado da demo com integração real; profile default no Compose é demo. |
| Conventional Commits e commits separados | Preferência explícita do usuário por commit para cada nova implementação, seguindo padrão existente. | Não agrupar várias features independentes num commit; verificar `git status` e preservar alterações do usuário. |

## 5. Código-chave para consultar

Não copie o projeto todo para o chat novo. Os arquivos abaixo concentram os contratos e fluxos que devem ser lidos antes de alterar a interface:

### Frontend

- `frontend/src/App.tsx`: layout, telas, URL `/stock/:id`, estados de edição/movimentação, notificações e carregamento de dados.
- `frontend/src/api.ts`: wrapper fetch/JWT, tratamento 401 e endpoints consumidos atualmente.
- `frontend/src/types.ts`: shape dos DTOs esperados pelo front.
- `frontend/src/styles.css`: regras visuais e media queries.
- `frontend/vite.config.ts`: proxy de `/api` para `http://localhost:8080` e porta 5173.
- `frontend/package.json`: versões e scripts.

Contrato mínimo de chamada:

```ts
const API_URL = import.meta.env.VITE_API_URL ?? '/api'
// request() inclui Accept, Content-Type quando há body e Bearer token do localStorage.
// login: POST /auth/login { username, password }
// editar: PUT /products/{id} { sku, name, description, basePrice }
// mínimo: PATCH /inventory/product/{id}/min-quantity { minQuantity }
// definir saldo: PATCH /inventory/product/{id}/quantity { productId, quantity }
// movimento: POST /stock-movement { productId, quantity, type, note }
```

### Backend

- `config/SecurityConfig.java`: matchers públicos e papéis globais.
- `config/security/JwtTokenService.java`, `UserTokenValidator.java`, `DatabaseUserDetailsService.java`: emissão/validação e claims do JWT.
- `controller/ProductController.java`, `InventoryController.java`, `StockMovementController.java`: contratos que dirigem as telas atuais.
- `service/ProductService.java`, `InventoryService.java`, `StockMovementService.java`: gravação e regra do estoque.
- `InventoryService.adjustStockWithHistory(...)`: correção recente que torna edição absoluta do saldo auditável.
- `event/StockChangedEvent.java`, `listener/StockSynchronizationListener.java`, `service/StockSynchronizationService.java` e processors: atualização remota e rastreabilidade.
- `integration/MarketplaceIntegrationPort.java`, `integration/adapter/DemoMarketplaceAdapter.java`, `MercadoLivreAdapter.java`: diferença entre ambiente demo e real.
- `src/main/resources/db/migration/V1__create_stock_sync_schema.sql` até `V10__track_product_deactivation_listing_pauses.sql`: esquema efetivo.
- `docker-compose.yml`, `Dockerfile`, `application*.properties`: execução e perfis.

## 6. Problemas e bugs

### Resolvidos

- **Frontend não conectava à API:** proxy Vite `/api` apontava ao backend local e removia o prefixo antes de encaminhar. Config em `frontend/vite.config.ts`.
- **Login e legibilidade pequenos/feedback ambíguo:** tela de login e estilos ampliados; erros distinguem 401/403, 429 e falha de conexão.
- **401 após restart da API:** bootstrap dos dados demo podia alterar/incrementar versão de autenticação da conta e invalidar JWT. `DemoDataBootstrap` foi ajustado para preservar sessão quando configuração/identidade não mudou; commit `3621eef` (`fix: preserve demo sessions across API restarts`).
- **Erro ao editar produto / requests concorrentes:** front passou a tratar e apresentar erro real e controlar submissão/ordem das alterações. Commit `b761f02`.
- **Movimentação sem nome de produto:** tabela e resumo exibem nome e SKU.
- **Edição de saldo não aparecia no histórico:** `/inventory/product/{id}/quantity` chamava ajuste direto sem `StockMovement`; agora controller chama `adjustStockWithHistory`, que registra delta atômico. Commit `6261944`.
- **Saldo editado sem audit trail:** mesma correção acima: aumento vira `MANUAL_INCREASE`, redução `MANUAL_DECREASE`, nota padrão explica edição; quantidade sem alteração retorna delta 0 e não escreve movimento.
- **Botão Atualizar duplicado:** removido botão dentro do painel e mantido o superior direito. Commit `afb26e3`.
- **Seleção de todos / ações em lote:** checkbox de cabeçalho seleciona os itens visíveis; ações de ativar/desativar são desabilitadas sem seleção.
- **Layout/ações no detalhe:** layout do detalhe foi centralizado e posição do botão editar/status refinada em commits de estilo até o estado atual.

### Conhecidos / pontos para revisar

- **README está desatualizado em um trecho:** a seção “Próximas evoluções” ainda diz que a interface web é planejada e que o projeto hoje é acessado pela API/Swagger, mas o frontend já existe e está rodando. Atualizar essa seção ao tratar documentação.
- **Mudanças locais não commitadas no snapshot atual:** `README.md` está modificado e `docs/` não rastreada, contendo `docs/postman/StockSync-Demo.postman_collection.json` e `StockSync-Local.postman_environment.json`, além do documento de handoff. Não sobrescrever nem incluir automaticamente essas alterações em outros commits. O handoff foi criado nesta tarefa e é a nova exceção rastreável dentro de `docs/`.
- **Contratos de movimentos têm semânticas diferentes:** edição define saldo absoluto (`quantity`) enquanto `POST /stock-movement` recebe quantidade delta e tipo. Não trocar um pelo outro no front.
- **A tela de movimentações é um agregado limitado no cliente:** `refresh()` carrega até 100 produtos, busca 10 registros recentes por produto, junta, ordena e corta para os 10 mais recentes globais. Não há endpoint atual de movimentos globais; uma tela de histórico extenso exigiria paginação/endpoint backend.
- **Telas de pedidos/canais/anúncios não foram implementadas:** são funcionalidades backend, não bugs de UI.
- **API apresenta `Page<T>` do Spring:** nas funções atuais o front pede `size=100` ou `size=10`; cuidado para não tratar resposta como array direto.
- **Rota SPA é manual:** verificar refresh/deep link de `/stock/:id` no Vite/deployment. O dev server faz fallback, mas qualquer host estático de produção também precisa reescrever caminhos para `index.html`.
- **Papel VIEWER:** pode consultar GETs, mas formulário/ações devem ficar desabilitados/escondidos se o front vier a expor login para usuários desse papel. A demo atual autentica como `OPERATOR`.
- **Relatos históricos:** em conversas anteriores o usuário relatou 401 no update; a correção do backend/propagação de erro foi feita. Se voltar, olhar Network tab e `GlobalExceptionHandler`, não esconder o status retornado.

## 7. Pendências e próximos passos

Ordem sugerida, alinhada ao escopo atual. Não iniciar expansão sem seguir o ritmo incremental que o usuário pediu.

1. **Conferir funcionamento visual atual** em `http://localhost:5173` (front dev) e `http://localhost:8080` (API). Confirmar login, fluxo de edição, histórico e rota `/stock/{id}` no navegador. Não refazer layout por iniciativa própria: o último pedido foi manter apenas o refresh superior direito.
2. **Corrigir a documentação do README**, que ainda afirma que a interface web está planejada; incluir como subir e usar a SPA. Manter a alteração já iniciada localmente e os arquivos Postman em `docs/`.
3. **Alinhar contrato de histórico com UX, se necessário:** atualmente o endpoint do front pagina 10 movimentos por produto e 10 falhas; o refresh carrega até 100 produtos, agrega os movimentos e mantém os 10 mais recentes globais para o painel e a tela. Se a tela Movimentações precisar de histórico completo, adicionar endpoint global paginado no backend, em vez de buscar indefinidamente por produto.
4. **Melhorar navegação/roteamento SPA** se deployment com URL profunda falhar; considerar uma biblioteca de roteamento só se a complexidade justificar. Hoje a rota manual já atende `/stock/:id`.
5. **Polir estados de loading/erro e acessibilidade/responsividade** com base em verificação real, preservando design simples e legível.
6. **Depois de validar o MVP visual**, combinar com o usuário a próxima área a implementar (pedidos, canais/anúncios, filtros/relatórios, administração); não presumir que todas fazem parte do próximo incremento.

### Último estado conhecido

- Branch local `main` alinhada com `origin/main`; HEAD `afb26e3` (`style: remove duplicate dashboard refresh button`).
- Push dos commits do front para GitHub foi concluído. O push exigiu rebase dos commits locais por um commit remoto de documentação; preservamos as mudanças não commitadas em `README.md` e `docs/`.
- Front foi compilado com sucesso nesta sessão por `docker exec stocksync-frontend npm run build` (TypeScript + Vite). O ambiente usado pelo agente tinha container `stocksync-frontend` escutando em 5173; `docker compose.yml` do repositório só define `postgres` e `app`, então esse container front parece ser iniciado por fora do Compose atual.
- API/Compose do projeto: `docker compose up --build` cria `postgres` e `app`; a pasta `frontend` roda separadamente com npm/Vite.
- Commit recente anterior ao handoff: `afb26e3 style: remove duplicate dashboard refresh button`; outros commits recentes cobrem edição/histórico, auth demo e detalhe.

## 8. Como rodar localmente

### Opção A — subir API e banco via Docker (recomendado)

Pré-requisitos: Git, Docker Engine/Desktop com Compose plugin. Node/npm só é necessário para iniciar o front fora de um container.

```bash
git clone https://github.com/ErikFerreira1/StockSync.git
cd StockSync
docker compose up --build
```

Compose sobe:

- PostgreSQL 17 em `localhost:5432`, DB `stocksync`, usuário `stocksync`, senha demo `stocksync-demo-db`;
- API Spring Boot em `http://localhost:8080`;
- Profile default do Compose: `demo`;
- Migrações Flyway automáticas e dados demo.

Swagger: `http://localhost:8080/swagger-ui.html`.

Credencial local demo: usuário `demo`, senha `stocksync-demo`. A demo inicia dois produtos: Cabo USB-C (`DEMO-USB-C`, qty 12, mínimo 3) e teclado (`DEMO-KEYBOARD`, qty 2, mínimo 5).

### Iniciar frontend com Node local

```bash
cd frontend
npm install
npm run dev
```

Abrir `http://localhost:5173`. Por padrão, `vite.config.ts` proxy `/api` para `http://localhost:8080`. Para apontar para outra origem em chamadas no browser, criar `.env.local`:

```dotenv
VITE_API_URL=http://localhost:8080
```

O `.env.local` é ignorado pelo Git; confirme o comportamento de proxy se usar URL absoluta. Scripts: `npm run dev`, `npm run build` (`tsc -b && vite build`), `npm run preview`.

### Subir frontend via container (se já houver imagem/container preparado)

O Compose versionado não define serviço frontend. Na sessão de desenvolvimento havia container externo chamado `stocksync-frontend`, baseado em `node:22-alpine`, publicado em 5173. Para buildar nele: `docker exec stocksync-frontend npm run build`. Em uma máquina sem esse container, use Node local ou configure o container separadamente.

### Variáveis relevantes

Os defaults do demo bastam para desenvolvimento. Para customizar, criar `.env` na raiz com, por exemplo:

```dotenv
APP_PORT=8080
POSTGRES_PORT=5432
POSTGRES_DB=stocksync
POSTGRES_USER=stocksync
POSTGRES_PASSWORD=senha-local
SPRING_PROFILES_ACTIVE=demo
DEMO_USERNAME=demo
DEMO_PASSWORD=stocksync-demo
JWT_SECRET=troque-por-um-segredo-com-pelo-menos-32-bytes
ENCRYPTION_KEY=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=
MERCADO_LIVRE_REDIRECT_URI=https://example.invalid/mercadolivre/callback
```

Outras configurações suportadas em `application.properties`: `SHOW_SQL`, `JWT_EXPIRATION`, `LOGIN_MAX_ATTEMPTS`, `LOGIN_RATE_WINDOW`, `BOOTSTRAP_ADMIN_USERNAME`, `BOOTSTRAP_ADMIN_PASSWORD`, `MERCADO_LIVRE_OAUTH_COOKIE_SECURE`. `POSTGRES_URL` é montada pelo Compose como JDBC interno (`postgres:5432`). Para perfil real Mercado Livre, configurar `SPRING_PROFILES_ACTIVE` sem `demo`, credenciais OAuth e redirect URI válidos. Nunca colocar credenciais reais em commits.

### Encerrar e inspecionar

```bash
docker compose down
# também apaga os dados do banco local:
docker compose down -v
```

Use `down -v` apenas quando for intencional resetar o volume PostgreSQL. Para consultar os logs: `docker compose logs -f app postgres`.

### Testes e builds

```bash
./mvnw test
./mvnw verify
```

`verify` inclui relatório JaCoCo em `target/site/jacoco/index.html` e testes de integração Testcontainers, portanto Docker precisa estar disponível. Build frontend: `cd frontend && npm run build`.

---

## Mensagem inicial sugerida para o chat novo

> Estou continuando o projeto StockSync existente neste repositório (Java 17/Spring Boot 3.5 + PostgreSQL no backend e React 18/TypeScript/Vite em `frontend/`). A interface já tem login, visão geral, estoque com edição/ativação, detalhe por produto em `/stock/:id`, entradas/saídas com histórico, notificações e tela de falhas de sincronização. Os commits estão na `main` até `afb26e3`; também há alterações locais ainda não commitadas em `README.md` e arquivos Postman/documentação em `docs/`, que devem ser preservados. Leia `docs/HANDOFF_WEB.md` e o estado atual do Git antes de alterar qualquer coisa. Continue do estado existente sem recriar funcionalidades nem refazer o design; o último ajuste foi remover o botão de atualizar duplicado e manter o botão superior direito. Próximo passo sugerido é conferir a aplicação no navegador e atualizar a documentação com o front já implementado, uma mudança por commit Conventional Commits."

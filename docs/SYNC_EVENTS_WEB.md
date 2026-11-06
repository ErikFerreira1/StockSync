# Histórico de sincronizações

A tela `/sync` consulta o histórico no backend, com paginação de 20 eventos. Abre mostrando falhas; o filtro Resultado permite consultar sucessos, pendências ou todos os resultados. Os filtros são aplicados em conjunto, pelo botão **Aplicar filtros**.

Filtros disponíveis:

- Resultado: `PENDING`, `SUCCESS` ou `FAILURE`.
- Origem: produto (estoque/status), pedido ou todas.
- Produto e canal, incluindo referências a canais inativos para consulta de histórico.
- ID do pedido local.
- Identificador externo completo do pedido, por igualdade exata.

A paginação mantém os filtros. Limpar filtros volta à primeira página e remove também o filtro inicial de falhas. A ordenação padrão usa `timestamp DESC, id DESC`, evitando resultados instáveis quando eventos têm o mesmo horário.

Eventos de pedido incluem falhas que possuem somente o identificador externo, antes de existir um pedido no banco local. Quando há um pedido local, a tela permite abrir seus detalhes. Eventos de produto permitem abrir um produto conhecido na listagem atual.

As mensagens utilizam as categorias seguras de erro existentes no backend; corpos de resposta externos, tokens e credenciais não são retornados. O contador de tentativas pertence a cada evento e não representa uma fila automática de reprocessamento.

## API

`GET /sync-events` recebe os filtros opcionais `status`, `scope` (`PRODUCT`/`ORDER`), `productId`, `salesChannelId`, `orderId` e `externalOrderId`, além de `page`, `size` e `sort`. IDs devem ser positivos; identificadores externos têm até 255 caracteres. Usuários autenticados ADMIN, OPERATOR e VIEWER podem consultar.

Os endpoints anteriores `/sync-events/failed` e `/sync-events/product/{productId}` continuam disponíveis para compatibilidade.

## Limites desta etapa

A tabela atual não distingue estoque de mudança de status em um evento de produto. Por isso, a tela identifica essa origem como produto/estoque/status e não oferece um botão genérico de retry. Reprocessamento exige identificar precisamente a operação e desenhar sua autorização e proteção contra duplicidade, principalmente na importação de pedidos. Esta etapa não executa chamadas ao marketplace nem altera estoque.

## Testes

- `./mvnw verify`: inclui consultas reais em PostgreSQL via Testcontainers para combinações de filtros, paginação, permissões, sanitização de erros e falhas de importação sem pedido local.
- `docker exec stocksync-frontend npm test`: testes de filtros, navegação para referências, paginação, estados vazio/erro, respostas antigas e codificação dos parâmetros.
- `docker exec stocksync-frontend npm run build`: TypeScript e Vite.

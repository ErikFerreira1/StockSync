# Pedidos na interface web

O menu **Pedidos** abre `/orders`. Os detalhes podem ser acessados diretamente em `/orders/{id}`, incluindo após recarregar a página ou usar voltar/avançar no navegador.

## Fluxos

- **Listagem:** paginação no servidor com 10 pedidos por página, ordenados por ID decrescente. Mostra identificador externo, canal, data, status, unidades e total dos itens. Os estados de carregamento, vazio e erro possuem mensagens próprias.
- **Detalhes:** consulta o pedido pelo ID, exibindo itens, preços, subtotais e total. Um pedido pode ser consultado mesmo quando estiver fora da página atual.
- **Novo pedido:** seleciona um canal ativo e produtos ativos, mesmo sem saldo. Permite vários itens, uma linha por produto, preço unitário a partir de zero e data/hora local convertida para `Instant` no envio. Uma revisão explícita antecede o POST. A criação registra o pedido como `PENDING`, sem baixar nem reservar estoque. A quantidade pode exceder o saldo atual, pois a disponibilidade só é garantida na confirmação do pagamento.
- **Confirmar pagamento:** registra localmente um pagamento já recebido, sem efetuar cobrança ou alterar pagamentos no Mercado Livre. O backend bloqueia o pedido, verifica todos os itens e baixa estoque atomicamente, marcando `PAID`. Repetições não fazem nova baixa; saldo insuficiente mantém o pedido pendente sem alterações parciais.
- **Cancelar no StockSync:** disponível para `PENDING` e `PAID`, com confirmação explícita. Devolve as quantidades e aciona a sincronização somente se o pedido tiver baixa ativa. Cancelar um pendente novo não altera o estoque. Não cancela a venda no Mercado Livre nem emite reembolso.
- **Sincronizar pedidos:** consulta pedidos pagos de um canal Mercado Livre ativo. Importa somente pedidos ainda não registrados, usando os vínculos de anúncios existentes. A confirmação avisa que a importação baixa estoque e pode atualizar anúncios. No perfil demo usa o adaptador simulado.

Uma resposta `204` da sincronização significa que a consulta terminou; não garante que todos os pedidos foram importados. O backend registra falhas individuais sem abortar o lote. A interface mostra os eventos de pedidos do canal encontrados nos últimos 10 eventos de falha, com indicação de que podem incluir tentativas anteriores. Não apresenta contagens de importação que a API não fornece.

ADMIN e OPERATOR podem escrever. VIEWER possui acesso de leitura. O frontend recupera os dados de exibição e o papel das claims do JWT ao recarregar; a autorização real continua sendo aplicada pelo Spring Security no backend.

## Compatibilidade dos pedidos antigos

A migração V12 cria `stock_deducted` e recupera a informação dos movimentos `SALE`/`CANCELLATION` ligados ao pedido. Não altera saldos nem presume que pagamentos antigos foram recebidos. Pendentes antigos com baixa ativa podem receber confirmação sem novo desconto, ou ser cancelados com devolução.

`PATCH /orders/{id}/confirm-payment` exige ADMIN ou OPERATOR. A resposta de pedidos inclui `stockDeducted`. Importações do Mercado Livre aceitam apenas status externo `paid`, criando e confirmando o pedido na mesma transação; falta de estoque desfaz toda a importação.

## Ajustes de contrato

`OrderService` mantém a relação entre pedido e itens em memória durante a criação, para a resposta de POST incluir os itens gravados. A listagem mantém a ordem da página original depois da busca dos itens com fetch join.

## Testes

- Backend: `./mvnw verify`. Os testes de regressão de contrato em `CoreWorkflowIntegrationTest` usam PostgreSQL 17 via Testcontainers e verificam os itens na resposta, a ordenação, a baixa e a devolução idempotente do estoque. A API de marketplace é simulada.
- Frontend: na pasta `frontend`, execute `npm ci`, `npm test` e `npm run build`. Com o container existente: `docker exec stocksync-frontend npm test` e `docker exec stocksync-frontend npm run build`.
- Testes de componentes e HTTP ficam em `frontend/tests`, usando Vitest, Testing Library e jsdom. As operações da API são simuladas; os testes não criam/cancelam pedidos nem importam vendas de uma conta real.

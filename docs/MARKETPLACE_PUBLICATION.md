# Publicação de anúncios no Mercado Livre

A aba **Anúncios** oferece dois fluxos: **Criar anúncio** publica um produto na conta autorizada; **Vincular existente** associa um anúncio já publicado. O vínculo não cria anúncios.

## Uso

1. Execute o backend sem o perfil `demo` e autorize o canal com uma conta ADMIN.
2. Entre como ADMIN ou OPERATOR e abra **Anúncios → Criar anúncio**.
3. Selecione produto ativo com estoque e canal autorizado. Busque uma categoria e escolha uma sugestão.
4. Preencha nome, preço inicial, tipo de anúncio, fotos HTTPS públicas, características, condições de venda e envio. Os campos são consultados na API para a categoria e conta selecionadas.
5. **Validar e revisar** consulta o validador do Mercado Livre sem publicar. Confira os dados e use **Confirmar e publicar** para criar o anúncio real.

O preço inicial pode diferir do preço-base local. Atualizações posteriores de preço continuam fora da sincronização atual. O saldo usado na publicação vem de `InventoryService`, sem alterar o estoque local. O ID e a URL retornados pelo Mercado Livre são vinculados automaticamente ao produto. Se o saldo mudar durante a criação externa, o fluxo existente de sincronização reconcilia o novo vínculo após o commit local.

Esta primeira versão atende produtos sem variações no Brasil, fotos por URL HTTPS e os modos de envio `me2`/`not_specified`, quando disponíveis para conta e categoria. Upload de arquivos, variações, publicações de catálogo e logística personalizada não fazem parte deste formulário. Regras adicionais da categoria/conta são verificadas pelo validador oficial; aceitação ou moderação continuam sob responsabilidade do Mercado Livre.

## API

- `GET /marketplace-publications/categories?salesChannelId=...&query=...`: sugestões de categoria.
- `GET /marketplace-publications/options?salesChannelId=...&categoryId=...`: atributos, condições de venda, limites, tipos de anúncio e envio.
- `POST /marketplace-publications/validate`: validação local e remota, sem criar anúncio; retorna `200` com `warnings` para revisão.
- `POST /marketplace-publications`: validação, publicação e vínculo local; retorna `201` com `listing` e `warnings`.

O corpo é `PublicationRequestDTO`. Cada publicação usa um `requestId` UUID. Não reutilize a identificação com dados diferentes. As rotas exigem ADMIN ou OPERATOR, inclusive as consultas de opções; VIEWER não pode usá-las. No perfil demo, as rotas de publicação real não são registradas.

O adaptador detecta `user_product_seller` em `/users/me` para enviar `family_name` em contas User Products e `title` nas contas do modelo anterior. A descrição é enviada separadamente, após persistir o ID externo. Uma falha na descrição retorna sucesso com `description_not_saved`; o anúncio permanece criado e a descrição pode ser completada no Mercado Livre.

O validador pode retornar HTTP 400 com `validation_error` contendo somente causas do tipo `warning`. Esses avisos não bloqueantes são preservados na revisão, incluindo eventuais ajustes de envio exigidos pela plataforma. Causas bloqueantes, desconhecidas ou ausentes continuam sendo rejeitadas. Essa interpretação vale somente para `/items/validate`: a criação exige uma resposta bem-sucedida com o ID externo, nunca apenas avisos.

## Repetição e resultados incertos

A migração V11 cria `marketplace_publications`. A reserva do UUID é atômica no PostgreSQL e confirmada antes do POST externo. O mesmo UUID retorna o vínculo existente ou recupera o vínculo a partir do ID externo persistido, sem executar outra publicação.

Se ocorrer timeout ou erro com resultado incerto, a reserva permanece em `STARTED` e bloqueia um novo POST para esse UUID. Não há garantia de atomicidade entre o Mercado Livre e o banco local: se o anúncio for criado e a resposta se perder antes de salvar seu ID, será necessário conferir os anúncios da conta e vincular o anúncio existente. Não gere outra identificação automaticamente nesse caso.

O navegador guarda a tentativa em `sessionStorage`, permitindo recuperar o mesmo pedido após recarregar a aba. Não limpe o armazenamento nem inicie outra publicação até conferir um resultado incerto. Rejeições explícitas de dados permitem corrigir o formulário e validar novamente.

## Verificação

Execute `./mvnw verify` e o build de `frontend`. Os testes de adaptador usam `MockRestServiceServer`; os testes de integração usam PostgreSQL 17 em Testcontainers e simulam apenas a API externa. Incluem persistência, repetição, concorrência, recuperação, validação, descrição parcial e permissões. Não criam anúncios em contas reais.

Referências oficiais: [Publicação](https://developers.mercadolivre.com.br/pt_br/publicacao-de-produtos), [User Products](https://developers.mercadolivre.com.br/pt_br/user-products), [Validador](https://developers.mercadolivre.com.br/pt_br/tendencias/validador-de-publicacoes), [Tipos de anúncio](https://developers.mercadolivre.com.br/pt_br/tutorial-tipos-de-publicacao-y-atualizacao-de-artigos).

import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { RefreshCw } from 'lucide-react'
import { api, ApiError } from './api'
import type { Page, Product, SalesChannel, SyncEvent, SyncEventFilters } from './types'

const defaults: SyncEventFilters = { status: 'FAILURE', scope: '', productId: '', salesChannelId: '', orderId: '', externalOrderId: '' }
const labels: Record<string, string> = { FAILURE: 'Falha', SUCCESS: 'Sucesso', PENDING: 'Pendente' }
const explanations: Record<string, string> = {
  'Marketplace integration failed': 'O marketplace não confirmou a operação. Confira a conexão, o anúncio e os dados enviados.',
  'Marketplace credential is not authorized': 'O canal não possui autorização válida. Solicite a conexão a um administrador.',
  'Insufficient stock': 'O estoque era insuficiente para concluir a operação.',
  'Synchronization failed': 'A sincronização falhou. Confira as condições do produto e do canal.',
}

export default function SyncEventsPage({ products, channels, onProduct, onOrder }: {
  products: Product[]; channels: SalesChannel[]; onProduct: (id: number) => void; onOrder: (id: number) => void
}) {
  const [draft, setDraft] = useState<SyncEventFilters>(defaults)
  const [filters, setFilters] = useState<SyncEventFilters>(defaults)
  const [page, setPage] = useState(0)
  const [revision, setRevision] = useState(0)
  const [result, setResult] = useState<Page<SyncEvent> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const sequence = useRef(0)

  useEffect(() => {
    let active = true
    const requestId = ++sequence.current
    setLoading(true); setError('')
    api.syncEvents(filters, page).then(data => {
      if (!active || requestId !== sequence.current) return
      if (data.totalPages > 0 && page >= data.totalPages) { setPage(data.totalPages - 1); return }
      setResult(data)
    }).catch(cause => {
      if (active && requestId === sequence.current) setError(cause instanceof ApiError
        ? cause.status === 403 ? 'Sua conta não tem permissão para consultar os eventos.' : cause.message
        : 'Não foi possível carregar as sincronizações. Confira sua conexão.')
    }).finally(() => { if (active && requestId === sequence.current) setLoading(false) })
    return () => { active = false }
  }, [filters, page, revision, products])

  function apply(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setPage(0); setFilters({ ...draft })
  }

  return <section className="card domain-card sync-events-card">
    <div className="domain-card-head"><div><h2>Histórico de sincronizações</h2><p className="muted">Estoque, status de anúncios e importação de pedidos, com resultados de cada operação.</p></div>
      <button className="filter-button" disabled={loading} onClick={() => setRevision(value => value + 1)}><RefreshCw size={15}/> Atualizar histórico</button></div>
    <form className="sync-filters" onSubmit={apply} aria-label="Filtros de sincronização">
      <label className="edit-field">Resultado<select value={draft.status} onChange={event => setDraft({ ...draft, status: event.target.value as SyncEventFilters['status'] })}><option value="">Todos</option><option value="FAILURE">Falhas</option><option value="SUCCESS">Sucessos</option><option value="PENDING">Pendentes</option></select></label>
      <label className="edit-field">Origem<select value={draft.scope} onChange={event => setDraft({ ...draft, scope: event.target.value as SyncEventFilters['scope'] })}><option value="">Todas</option><option value="PRODUCT">Produto (estoque/status)</option><option value="ORDER">Pedido</option></select></label>
      <label className="edit-field">Canal<select value={draft.salesChannelId} onChange={event => setDraft({ ...draft, salesChannelId: event.target.value })}><option value="">Todos os canais</option>{channels.map(channel => <option key={channel.id} value={channel.id}>{channel.name}{channel.active ? '' : ' (inativo)'}</option>)}</select></label>
      <label className="edit-field">Produto<select value={draft.productId} onChange={event => setDraft({ ...draft, productId: event.target.value })}><option value="">Todos os produtos</option>{products.map(product => <option key={product.id} value={product.id}>{product.name}</option>)}</select></label>
      <label className="edit-field">ID do pedido local<input type="number" min="1" step="1" value={draft.orderId} onChange={event => setDraft({ ...draft, orderId: event.target.value })}/></label>
      <label className="edit-field">Identificador externo do pedido<input maxLength={255} value={draft.externalOrderId} onChange={event => setDraft({ ...draft, externalOrderId: event.target.value })} placeholder="Identificador completo"/></label>
      <div className="review-actions"><button type="button" className="filter-button" onClick={() => { const cleared = { ...defaults, status: '' as const }; setDraft(cleared); setFilters(cleared); setPage(0) }}>Limpar filtros</button><button className="primary">Aplicar filtros</button></div>
    </form>
    {error ? <div className="orders-error" role="alert"><p>{error}</p><button className="filter-button" onClick={() => setRevision(value => value + 1)}>Tentar novamente</button></div>
      : loading ? <p className="muted" role="status">Carregando sincronizações…</p>
      : !result?.content.length ? <div className="no-results"><b>Nenhum evento encontrado</b><span>Não há registros para os filtros selecionados.</span></div>
      : <><div className="table-scroll"><table><thead><tr><th>RESULTADO</th><th>REFERÊNCIA</th><th>CANAL</th><th>DATA</th><th>TENTATIVAS</th><th>DETALHE</th></tr></thead>
        <tbody>{result.content.map(event => {
          const orderEvent = event.orderId !== null || !!event.externalOrderId
          const product = products.find(product => product.id === event.productId)
          return <tr key={event.id}><td><span className={`status-chip ${event.status === 'SUCCESS' ? 'on' : 'off'}`}><i/>{labels[event.status] || event.status}</span></td>
            <td><b>{orderEvent ? event.externalOrderId || `Pedido #${event.orderId}` : product?.name || (event.productId ? `Produto #${event.productId}` : 'Sem referência')}</b>
              <small className="domain-subline">Evento #{event.id} · {orderEvent ? 'Pedido' : 'Produto / estoque / status'}</small>
              {orderEvent && event.orderId && <button className="row-adjust" onClick={() => onOrder(event.orderId!)}>Abrir pedido #{event.orderId}</button>}
              {!orderEvent && product && <button className="row-adjust" onClick={() => onProduct(product.id)}>Abrir produto</button>}</td>
            <td>{channels.find(channel => channel.id === event.salesChannelId)?.name || `Canal #${event.salesChannelId}`}</td>
            <td>{new Date(event.occurredAt).toLocaleString('pt-BR')}</td><td title="Tentativas registradas neste evento">{event.attempts}</td>
            <td className="sync-event-detail">{event.status === 'SUCCESS' ? 'Operação concluída.' : event.status === 'PENDING' ? 'Aguardando processamento.' : explanations[event.errorMessage || ''] || 'Falha de sincronização.'}</td></tr>
        })}</tbody></table></div>
        <div className="orders-pagination"><span>{result.totalElements} eventos · Página {page + 1} de {Math.max(1, result.totalPages)}</span><div>
          <button className="filter-button" disabled={page === 0} onClick={() => setPage(value => value - 1)}>Anterior</button>
          <button className="filter-button" disabled={page + 1 >= result.totalPages} onClick={() => setPage(value => value + 1)}>Próxima</button></div></div></>}
  </section>
}

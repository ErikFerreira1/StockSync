import { useCallback, useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { Plus, RefreshCw, X, Trash2 } from 'lucide-react'
import { api, ApiError } from './api'
import type { DemoMarketplaceListing, Order, OrderRequest, OrderStatus, Page, Product, SalesChannel } from './types'

const statusLabels: Record<OrderStatus, string> = {
  PENDING: 'Pendente', PAID: 'Pago', CANCELED: 'Cancelado', REFUNDED: 'Reembolsado', COMPLETED: 'Concluído',
}
const money = (value: number) => value.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
const date = (value: string) => new Date(value).toLocaleString('pt-BR')
const total = (order: Order) => order.items.reduce((sum, item) => sum + Math.round(item.subtotal * 100), 0) / 100
const canCancel = (order: Order) => order.status === 'PENDING' || order.status === 'PAID'

function message(cause: unknown) {
  if (!(cause instanceof ApiError)) return 'Não foi possível acessar a API. Confira a conexão e os pedidos antes de repetir a operação.'
  if (cause.status === 403) return 'Sua conta não tem permissão para esta operação.'
  if (cause.status === 401) return 'Sua sessão expirou. Entre novamente.'
  if (/Insufficient stock/i.test(cause.message)) return 'Estoque insuficiente. Atualize os dados e confira as quantidades dos itens.'
  if (cause.status === 409 && /constraint|duplicate/i.test(cause.message)) return 'Já existe um pedido com esse identificador no canal. Confira a listagem antes de reenviar.'
  return [...cause.fieldErrors.map(field => field.message), cause.message].filter(Boolean).join(' · ')
}

function Status({ status }: { status: OrderStatus }) {
  return <span className={`status-chip ${status === 'CANCELED' || status === 'REFUNDED' ? 'off' : 'on'}`}><i/>{statusLabels[status] || status}</span>
}

type Props = {
  products: Product[]
  channels: SalesChannel[]
  role?: string
  selectedId?: number
  onSelect: (id?: number) => void
  onStockChanged: () => void
  demoMode: boolean
}

export default function OrdersPage({ products, channels, role, selectedId, onSelect, onStockChanged, demoMode }: Props) {
  const writable = role === 'ADMIN' || role === 'OPERATOR'
  const [page, setPage] = useState(0)
  const [revision, setRevision] = useState(0)
  const [orders, setOrders] = useState<Page<Order> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [creating, setCreating] = useState(false)
  const [simulating, setSimulating] = useState(false)
  const [detail, setDetail] = useState<Order | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [detailError, setDetailError] = useState('')
  const [confirmCancel, setConfirmCancel] = useState(false)
  const [canceling, setCanceling] = useState(false)
  const [confirmPayment, setConfirmPayment] = useState(false)
  const [paying, setPaying] = useState(false)
  const [synchronizing, setSynchronizing] = useState(false)
  const [syncDialog, setSyncDialog] = useState(false)
  const [syncChannelId, setSyncChannelId] = useState('')
  const [syncError, setSyncError] = useState('')
  const [syncNotice, setSyncNotice] = useState('')
  const [showSyncFailures, setShowSyncFailures] = useState(false)
  const [failures, setFailures] = useState<{ id: number; externalOrderId: string; occurredAt: string; errorMessage: string | null }[]>([])
  const listSequence = useRef(0)
  const detailSequence = useRef(0)
  const mounted = useRef(true)
  useEffect(() => { mounted.current = true; return () => { mounted.current = false } }, [])
  const reload = useCallback(() => setRevision(value => value + 1), [])

  useEffect(() => {
    const sequence = ++listSequence.current
    setLoading(true); setError('')
    api.orders(page).then(result => {
      if (!mounted.current || sequence !== listSequence.current) return
      if (result.totalPages > 0 && page >= result.totalPages) { setPage(result.totalPages - 1); return }
      setOrders(result)
    }).catch(cause => { if (mounted.current && sequence === listSequence.current) setError(message(cause)) })
      .finally(() => { if (mounted.current && sequence === listSequence.current) setLoading(false) })
    return () => { ++listSequence.current }
  }, [page, products, revision])

  useEffect(() => {
    const sequence = ++detailSequence.current
    setDetail(null); setDetailError(''); setConfirmCancel(false); setConfirmPayment(false)
    if (selectedId === undefined) { setDetailLoading(false); return }
    setDetailLoading(true)
    api.order(selectedId).then(result => { if (mounted.current && sequence === detailSequence.current) setDetail(result) })
      .catch(cause => { if (mounted.current && sequence === detailSequence.current) setDetailError(message(cause)) })
      .finally(() => { if (mounted.current && sequence === detailSequence.current) setDetailLoading(false) })
    return () => { ++detailSequence.current }
  }, [selectedId])

  async function cancel() {
    if (!detail || !writable || !confirmCancel || canceling) return
    setCanceling(true); setDetailError('')
    try {
      const canceled = await api.cancelOrder(detail.id)
      if (!mounted.current) return
      setDetail(canceled); setConfirmCancel(false); setNotice(detail.stockDeducted
        ? 'Pedido cancelado no StockSync. As quantidades foram devolvidas ao estoque local.'
        : 'Pedido pendente cancelado. O estoque não foi alterado.')
      reload(); onStockChanged()
    } catch (cause) { if (mounted.current) setDetailError(message(cause)) }
    finally { if (mounted.current) setCanceling(false) }
  }

  async function pay() {
    if (!detail || !writable || !confirmPayment || paying || canceling) return
    setPaying(true); setDetailError('')
    try {
      const paid = await api.confirmOrderPayment(detail.id)
      if (!mounted.current) return
      setDetail(paid); setConfirmPayment(false)
      setNotice(detail.stockDeducted ? 'Pagamento registrado. O estoque já havia sido baixado e não foi descontado novamente.'
        : 'Pagamento confirmado. As quantidades foram baixadas do estoque local.')
      reload(); onStockChanged()
    } catch (cause) { if (mounted.current) setDetailError(message(cause)) }
    finally { if (mounted.current) setPaying(false) }
  }

  async function synchronize() {
    if (!syncChannelId || !writable || synchronizing) return
    setSynchronizing(true); setSyncError(''); setSyncNotice(''); setShowSyncFailures(false)
    try {
      await api.synchronizeOrders(Number(syncChannelId))
      if (!mounted.current) return
      setSyncNotice('Consulta concluída. Confira os pedidos e os registros de falha abaixo; o canal pode retornar pedidos que não puderam ser importados.')
      setPage(0); reload(); onStockChanged()
      try {
        const failed = await api.failedSyncs()
        if (mounted.current) {
          setFailures(failed.content.filter(event => event.salesChannelId === Number(syncChannelId) && !!event.externalOrderId)
            .map(event => ({ id: event.id, externalOrderId: event.externalOrderId!, occurredAt: event.occurredAt, errorMessage: event.errorMessage })))
          setShowSyncFailures(true)
        }
      } catch { if (mounted.current) setSyncError('A consulta terminou, mas não foi possível carregar o histórico de falhas. Confira a aba Sincronizações.') }
    } catch (cause) { if (mounted.current) setSyncError(message(cause)) }
    finally { if (mounted.current) setSynchronizing(false) }
  }

  return <>
    <section className="card domain-card orders-card" aria-label="Pedidos">
      <div className="domain-card-head"><div><h2>Pedidos de venda</h2><p className="muted">Acompanhe os itens vendidos e seus efeitos no estoque.</p></div>
        <div className="review-actions"><button className="filter-button" onClick={reload} disabled={loading || synchronizing}><RefreshCw size={15}/> Atualizar pedidos</button>
          {writable && <><button className="filter-button" onClick={() => { setSyncDialog(true); setSyncError(''); setSyncNotice(''); setShowSyncFailures(false) }} disabled={!channels.some(c => c.active && c.type === 'MERCADO_LIVRE')}><RefreshCw size={15}/> Sincronizar pedidos</button>
            {demoMode && <button className="filter-button" onClick={() => setSimulating(true)} disabled={!channels.some(c => c.active && c.type === 'MERCADO_LIVRE')}>Simular venda</button>}
            <button className="primary" onClick={() => { setCreating(true); setNotice('') }} disabled={!products.some(p => p.active) || !channels.some(c => c.active)}><Plus size={15}/> Novo pedido</button></>}
        </div></div>
      {notice && <p className="orders-notice" role="status">{notice}</p>}
      {error ? <div className="orders-error" role="alert"><p>{error}</p><button className="filter-button" onClick={reload}>Tentar novamente</button></div>
        : loading ? <p className="muted" role="status">Carregando pedidos…</p>
        : !orders?.content.length ? <div className="no-results"><b>Nenhum pedido registrado</b><span>{writable ? 'Cadastre um pedido ou consulte os pedidos do canal conectado.' : 'Os pedidos aparecerão aqui quando forem registrados.'}</span></div>
        : <><div className="table-scroll"><table><thead><tr><th>PEDIDO</th><th>CANAL</th><th>DATA</th><th>STATUS</th><th>ITENS</th><th>TOTAL</th><th>AÇÕES</th></tr></thead>
          <tbody>{orders.content.map(order => <tr key={order.id}><td><b>{order.externalOrderId}</b><small className="domain-subline">#{order.id}</small></td>
            <td>{channels.find(c => c.id === order.salesChannelId)?.name || `Canal #${order.salesChannelId}`}</td><td>{date(order.orderDate)}</td><td><Status status={order.status}/></td>
            <td>{order.items.reduce((sum, item) => sum + item.quantity, 0)} un.</td><td>{money(total(order))}</td><td><button className="row-adjust" onClick={() => onSelect(order.id)} aria-label={`Detalhes do pedido ${order.externalOrderId}`}>Ver detalhes</button></td></tr>)}</tbody></table></div>
          <div className="orders-pagination"><span>{orders.totalElements} pedidos · Página {page + 1} de {Math.max(1, orders.totalPages)}</span>
            <div><button className="filter-button" disabled={page === 0} onClick={() => setPage(value => value - 1)}>Anterior</button><button className="filter-button" disabled={page + 1 >= orders.totalPages} onClick={() => setPage(value => value + 1)}>Próxima</button></div></div></>}
    </section>

    {simulating && demoMode && writable && <DemoSaleDialog channels={channels} onClose={() => setSimulating(false)} onImport={channelId => {
      setSimulating(false); setSyncChannelId(String(channelId)); setSyncDialog(true); setSyncError(''); setSyncNotice(''); setShowSyncFailures(false)
    }}/>}

    {creating && <OrderCreateDialog products={products} channels={channels} onClose={() => setCreating(false)} onCreated={order => {
      setCreating(false); setPage(0); reload(); setNotice('Pedido pendente cadastrado, sem baixar ou reservar estoque.'); onStockChanged(); onSelect(order.id)
    }}/>}

    {selectedId !== undefined && <div className="overlay"><section className="dialog orders-dialog" role="dialog" aria-modal="true" aria-labelledby="order-detail-title">
      <div className="review-top"><div><p className="date">PEDIDOS / DETALHES</p><h2 id="order-detail-title">{detail?.externalOrderId || `Pedido #${selectedId}`}</h2></div><button className="close" aria-label="Fechar detalhes" disabled={canceling || paying} onClick={() => onSelect()}><X size={19}/></button></div>
      {detailLoading && <p className="muted" role="status">Carregando detalhes…</p>}{detailError && <p className="form-error" role="alert">{detailError}</p>}
      {detail && <><div className="order-meta"><Status status={detail.status}/><span>{date(detail.orderDate)}</span><span>{channels.find(c => c.id === detail.salesChannelId)?.name || `Canal #${detail.salesChannelId}`}</span></div>
        <div className="table-scroll"><table><thead><tr><th>PRODUTO</th><th>QTD.</th><th>PREÇO UNITÁRIO</th><th>SUBTOTAL</th></tr></thead><tbody>{detail.items.map(item => <tr key={item.id}><td>{item.productName}</td><td>{item.quantity}</td><td>{money(item.unitPrice)}</td><td>{money(item.subtotal)}</td></tr>)}</tbody></table></div>
        <p className="order-total">Total do pedido <b>{money(total(detail))}</b></p>
        <p className="connection-help">{detail.stockDeducted ? 'Este pedido já teve baixa de estoque. A confirmação do pagamento não repetirá a baixa.' : 'Este pedido não possui baixa de estoque ativa. Pedidos pendentes não reservam quantidades.'}</p>
        {writable && typeof detail.stockDeducted === 'boolean' && detail.status === 'PENDING' && !confirmCancel && !confirmPayment && <div className="review-actions"><button className="primary" onClick={() => setConfirmPayment(true)}>Confirmar pagamento</button></div>}
        {writable && confirmPayment && <div className="order-cancel-confirm" role="group" aria-label="Confirmar pagamento"><h3>O pagamento deste pedido já foi recebido?</h3>
          <p>Esta ação registra o pagamento no StockSync; não efetua cobrança nem altera o pagamento no Mercado Livre. {detail.stockDeducted ? 'O estoque já foi baixado e não será descontado novamente.' : 'O saldo será conferido e baixado agora, com atualização dos anúncios elegíveis. Se faltar estoque, o pedido continuará pendente.'}</p>
          <div className="review-actions"><button className="filter-button" disabled={paying} onClick={() => setConfirmPayment(false)}>Voltar</button><button className="primary" disabled={paying} onClick={() => void pay()}>{paying ? 'Confirmando…' : 'Registrar pagamento recebido'}</button></div></div>}
        {writable && typeof detail.stockDeducted === 'boolean' && canCancel(detail) && !confirmPayment && (confirmCancel ? <div className="order-cancel-confirm" role="group" aria-label="Confirmar cancelamento"><h3>Cancelar este pedido no StockSync?</h3>
          <p>{detail.stockDeducted ? 'As quantidades serão devolvidas ao estoque local e enviadas aos anúncios elegíveis.' : 'Este pedido não baixou estoque; o cancelamento não altera o saldo.'} Esta ação não cancela a venda no Mercado Livre nem gera reembolso.</p>
          <div className="review-actions"><button className="filter-button" disabled={canceling} onClick={() => setConfirmCancel(false)}>Manter pedido</button><button className="primary danger-action" disabled={canceling} onClick={() => void cancel()}>{canceling ? 'Cancelando…' : 'Confirmar cancelamento'}</button></div>
        </div> : <div className="review-actions"><button className="filter-button" onClick={() => setConfirmCancel(true)}>Cancelar no StockSync</button></div>)}</>}
    </section></div>}

    {syncDialog && <div className="overlay"><section className="dialog product-edit-dialog orders-dialog" role="dialog" aria-modal="true" aria-labelledby="order-sync-title">
      <div className="review-top"><div><p className="date">PEDIDOS / IMPORTAÇÃO</p><h2 id="order-sync-title">Sincronizar pedidos</h2></div><button className="close" aria-label="Fechar sincronização" disabled={synchronizing} onClick={() => setSyncDialog(false)}><X size={19}/></button></div>
      <p className="connection-help">{demoMode ? 'No modo demo, a consulta usa o simulador.' : 'Busque pedidos pagos da conta do Mercado Livre. Os anúncios precisam estar vinculados aos produtos do StockSync.'} A importação baixa o estoque local e pode atualizar os anúncios vinculados.</p>
      <label className="edit-field">Canal de venda<select value={syncChannelId} disabled={synchronizing} onChange={event => { setSyncChannelId(event.target.value); setSyncNotice(''); setSyncError(''); setShowSyncFailures(false) }}><option value="">Selecione um canal autorizado</option>{channels.filter(c => c.active && c.type === 'MERCADO_LIVRE').map(c => <option value={c.id} key={c.id}>{c.name}</option>)}</select></label>
      {syncError && <p className="form-error" role="alert">{syncError}</p>}{syncNotice && <p className="orders-notice" role="status">{syncNotice}</p>}
      {showSyncFailures && <section className="order-sync-failures"><h3>Falhas recentes do canal</h3><p className="field-help">Registros encontrados entre os últimos eventos de falha. Podem incluir tentativas anteriores.</p>
        {failures.length ? <ul>{failures.map(failure => <li key={failure.id}><b>{failure.externalOrderId}</b> · {date(failure.occurredAt)}<br/>{failure.errorMessage || 'Falha de importação'}</li>)}</ul> : <p>Nenhuma falha de pedido encontrada nesse recorte. Confira a aba Sincronizações para outros registros.</p>}</section>}
      <div className="review-actions"><button className="filter-button" disabled={synchronizing} onClick={() => setSyncDialog(false)}>Fechar</button><button className="primary" disabled={synchronizing || !syncChannelId} onClick={() => void synchronize()}>{synchronizing ? 'Consultando…' : 'Buscar pedidos no canal'}</button></div>
    </section></div>}
  </>
}

type DraftItem = { key: number; productId: string; quantity: string; unitPrice: string }
function localDateTime() { const date = new Date(); return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16) }

function OrderCreateDialog({ products, channels, onClose, onCreated }: {
  products: Product[]; channels: SalesChannel[]; onClose: () => void; onCreated: (order: Order) => void
}) {
  const [items, setItems] = useState<DraftItem[]>([{ key: 1, productId: '', quantity: '1', unitPrice: '' }])
  const nextKey = useRef(2)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [review, setReview] = useState<OrderRequest | null>(null)
  const [externalId, setExternalId] = useState('')
  const [channelId, setChannelId] = useState('')
  const [orderDate, setOrderDate] = useState(localDateTime)
  const available = products.filter(product => product.active)
  const subtotal = items.reduce((sum, item) => sum + Math.round(Number(item.unitPrice || 0) * 100) * Number(item.quantity || 0), 0) / 100

  function update(key: number, patch: Partial<DraftItem>) { setReview(null); setItems(current => current.map(item => item.key === key ? { ...item, ...patch } : item)) }
  function prepare(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setError('')
    if (!externalId.trim()) { setError('Informe o identificador do pedido no canal.'); return }
    if (new Set(items.map(item => item.productId)).size !== items.length) { setError('Use uma única linha para cada produto.'); return }
    for (const item of items) {
      const product = available.find(product => product.id === Number(item.productId))
      if (!product || !Number.isInteger(Number(item.quantity)) || Number(item.quantity) < 1) {
        setError('Confira os produtos e informe quantidades inteiras maiores que zero.'); return
      }
      if (!Number.isFinite(Number(item.unitPrice)) || Number(item.unitPrice) < 0 || item.unitPrice.trim() === '') { setError('Informe um preço unitário válido.'); return }
    }
    const parsedDate = new Date(orderDate)
    if (!Number.isFinite(parsedDate.getTime())) { setError('Informe uma data válida.'); return }
    setReview({ externalOrderId: externalId.trim(), salesChannelId: Number(channelId), orderDate: parsedDate.toISOString(),
      items: items.map(item => ({ productId: Number(item.productId), quantity: Number(item.quantity), unitPrice: Number(item.unitPrice) })) })
  }

  async function save() {
    if (!review || saving) return
    setSaving(true); setError('')
    try { const order = await api.createOrder(review); onCreated(order) }
    catch (cause) { setError(message(cause)); setReview(null) }
    finally { setSaving(false) }
  }

  return <div className="overlay"><section className="dialog product-edit-dialog orders-dialog order-create-dialog" role="dialog" aria-modal="true" aria-labelledby="new-order-title">
    <div className="review-top"><div><p className="date">PEDIDOS / CADASTRO</p><h2 id="new-order-title">{review ? 'Revisar pedido' : 'Novo pedido'}</h2></div><button className="close" aria-label="Fechar cadastro" disabled={saving} onClick={onClose}><X size={19}/></button></div>
    {error && <p className="form-error" role="alert">{error}</p>}
    {review ? <><p className="connection-help">O pedido será registrado como pendente, sem baixar nem reservar estoque. A disponibilidade será conferida ao confirmar o pagamento.</p>
      <dl className="publication-review"><dt>Identificador</dt><dd>{review.externalOrderId}</dd><dt>Canal</dt><dd>{channels.find(c => c.id === review.salesChannelId)?.name}</dd><dt>Data</dt><dd>{date(review.orderDate)}</dd></dl>
      <ul className="order-review-items">{review.items.map(item => <li key={item.productId}>{products.find(p => p.id === item.productId)?.name} · {item.quantity} un. × {money(item.unitPrice)}</li>)}</ul>
      <p className="order-total">Total <b>{money(subtotal)}</b></p><div className="review-actions"><button className="filter-button" disabled={saving} onClick={() => setReview(null)}>Voltar e editar</button><button className="primary" disabled={saving} onClick={() => void save()}>{saving ? 'Cadastrando…' : 'Criar pedido pendente'}</button></div>
    </> : <form onSubmit={prepare}>
      <p className="connection-help">Crie um pedido pendente. O cadastro não baixa nem reserva estoque e não cria uma venda no Mercado Livre. A baixa acontece somente ao confirmar o pagamento recebido.</p>
      <label className="edit-field">Identificador do pedido<input required maxLength={255} value={externalId} onChange={event => setExternalId(event.target.value)} placeholder="Número do pedido no canal"/></label>
      <label className="edit-field">Canal de venda<select required value={channelId} onChange={event => setChannelId(event.target.value)}><option value="">Selecione o canal</option>{channels.filter(c => c.active).map(channel => <option key={channel.id} value={channel.id}>{channel.name}</option>)}</select></label>
      <label className="edit-field">Data do pedido<input type="datetime-local" required value={orderDate} onChange={event => setOrderDate(event.target.value)}/></label>
      <h3>Itens do pedido</h3>{items.map((item, index) => <fieldset className="order-item-editor" key={item.key}><legend>Item {index + 1}</legend>
        <label className="edit-field">Produto do item {index + 1}<select required value={item.productId} onChange={event => { const product = available.find(p => p.id === Number(event.target.value)); update(item.key, { productId: event.target.value, unitPrice: String(product?.basePrice ?? '') }) }}>
          <option value="">Selecione um produto</option>{available.map(product => <option key={product.id} value={product.id} disabled={items.some(other => other.key !== item.key && Number(other.productId) === product.id)}>{product.name} · {product.availableQuantity} un. disponíveis</option>)}</select></label>
        <div className="order-item-numbers"><label className="edit-field">Quantidade do item {index + 1}<input required type="number" min="1" step="1" value={item.quantity} onChange={event => update(item.key, { quantity: event.target.value })}/></label>
          <label className="edit-field">Preço unitário do item {index + 1}<input required type="number" min="0" step="0.01" value={item.unitPrice} onChange={event => update(item.key, { unitPrice: event.target.value })}/></label></div>
        <button className="row-adjust" type="button" disabled={items.length === 1} aria-label={`Remover item ${index + 1}`} onClick={() => setItems(current => current.filter(row => row.key !== item.key))}><Trash2 size={14}/> Remover item</button>
      </fieldset>)}
      <button type="button" className="filter-button" disabled={items.length >= available.length} onClick={() => setItems(current => [...current, { key: nextKey.current++, productId: '', quantity: '1', unitPrice: '' }])}><Plus size={15}/> Adicionar item</button>
      <p className="order-total">Total <b>{money(subtotal)}</b></p><div className="review-actions"><button type="button" className="filter-button" onClick={onClose}>Cancelar</button><button className="primary">Revisar pedido</button></div>
    </form>}
  </section></div>
}

function DemoSaleDialog({ channels, onClose, onImport }: {
  channels: SalesChannel[]; onClose: () => void; onImport: (channelId: number) => void
}) {
  const [channelId, setChannelId] = useState('')
  const [listingId, setListingId] = useState('')
  const [quantity, setQuantity] = useState('1')
  const [listings, setListings] = useState<DemoMarketplaceListing[]>([])
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [created, setCreated] = useState('')
  useEffect(() => {
    let current = true
    setListings([]); setListingId(''); setError('')
    if (!channelId) { setLoading(false); return }
    setLoading(true)
    api.demoOrderListings(Number(channelId)).then(result => { if (current) setListings(result) })
      .catch(cause => { if (current) setError(message(cause)) })
      .finally(() => { if (current) setLoading(false) })
    return () => { current = false }
  }, [channelId])
  const listing = listings.find(item => item.listingId === listingId)
  async function simulate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (saving || !listing || !Number.isInteger(Number(quantity)) || Number(quantity) < 1 || Number(quantity) > listing.availableQuantity) return
    setSaving(true); setError('')
    try {
      const result = await api.simulateDemoSale({ salesChannelId: Number(channelId), listingId, quantity: Number(quantity) })
      setCreated(result.externalOrderId)
    } catch (cause) { setError(message(cause)) }
    finally { setSaving(false) }
  }
  return <div className="overlay"><section className="dialog product-edit-dialog orders-dialog" role="dialog" aria-modal="true" aria-labelledby="demo-sale-title">
    <div className="review-top"><h2 id="demo-sale-title">Simular venda</h2><button className="close" aria-label="Fechar simulação" disabled={saving} onClick={onClose}><X size={19}/></button></div>
    <p className="connection-help">Gere um pedido pago no marketplace simulado. Depois, sincronize os pedidos para importá-lo e baixar o estoque do ambiente demo. Não cria vendas na conta real do Mercado Livre.</p>
    {error && <p className="form-error" role="alert">{error}</p>}
    {created ? <><p className="orders-notice" role="status">Venda simulada criada: {created}. O estoque local será baixado na importação.</p>
      <div className="review-actions"><button className="filter-button" onClick={onClose}>Fechar</button><button className="primary" onClick={() => onImport(Number(channelId))}>Ir para sincronização</button></div></>
      : <form onSubmit={simulate}>
        <label className="edit-field">Canal da simulação<select required disabled={saving} value={channelId} onChange={event => setChannelId(event.target.value)}><option value="">Selecione o canal</option>{channels.filter(c => c.active && c.type === 'MERCADO_LIVRE').map(c => <option key={c.id} value={c.id}>{c.name}</option>)}</select></label>
        {loading && <p role="status">Carregando anúncios…</p>}
        {channelId && !loading && !error && !listings.length && <p>Nenhum anúncio ativo com saldo neste canal.</p>}
        <label className="edit-field">Anúncio da simulação<select required disabled={loading || saving} value={listingId} onChange={event => { setListingId(event.target.value); setQuantity('1') }}><option value="">Selecione o anúncio</option>{listings.map(item => <option key={item.listingId} value={item.listingId}>{item.title} · {item.listingId} · {item.availableQuantity} un.</option>)}</select></label>
        <label className="edit-field">Quantidade da simulação<input required type="number" min="1" max={listing?.availableQuantity} step="1" disabled={saving} value={quantity} onChange={event => setQuantity(event.target.value)}/></label>
        {listing && <p>Total: {money(listing.price * Number(quantity || 0))}</p>}
        <div className="review-actions"><button type="button" className="filter-button" disabled={saving} onClick={onClose}>Cancelar</button><button className="primary" disabled={saving || loading || !listing}>{saving ? 'Gerando…' : 'Gerar venda simulada'}</button></div>
      </form>}
  </section></div>
}

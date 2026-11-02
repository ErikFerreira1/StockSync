import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Activity, AlertTriangle, ArrowUpFromLine, ArrowDownToLine, Bell, Boxes, ChevronDown, ChevronRight, LayoutDashboard, LogOut, Package, Search, Settings2, X, Pencil, Power, Plus, Store, Link2 } from 'lucide-react'
import { login, clearToken, getToken, api, getLoginError, ApiError } from './api'
import type { ChannelType, DemoMarketplaceListing, IntegrationCredential, ListingStatus, User, Product, Inventory, MarketplaceListing, SalesChannel, StockMovement, SyncEvent } from './types'

type Screen = 'dashboard' | 'products' | 'movements' | 'sync' | 'product-detail' | 'channels' | 'listings' | 'demo-marketplace'
type Data = { products: Product[]; stock: Map<number, Inventory>; moves: StockMovement[]; syncs: SyncEvent[]; channels: SalesChannel[]; listings: MarketplaceListing[]; demoListings: DemoMarketplaceListing[]; demoMode: boolean }
type AppNotification = { id: string; kind: 'stock' | 'sync' | 'movement'; title: string; detail: string; productId?: number }
const empty: Data = { products: [], stock: new Map(), moves: [], syncs: [], channels: [], listings: [], demoListings: [], demoMode: false }
function screenFromPath(path: string): Screen {
  if (path === '/stock') return 'products'
  if (/^\/stock\/\d+$/.test(path)) return 'product-detail'
  if (path === '/movements') return 'movements'
  if (path === '/sync') return 'sync'
  if (path === '/dashboard') return 'dashboard'
  if (path === '/channels') return 'channels'
  if (path === '/listings') return 'listings'
  if (path === '/demo-marketplace' || path.startsWith('/demo-marketplace/')) return 'demo-marketplace'
  return 'products'
}
function pathFromScreen(screen: Screen, productId?: number, listingId?: string): string {
  if (screen === 'product-detail' && productId !== undefined) return `/stock/${productId}`
  if (screen === 'demo-marketplace' && listingId) return `/demo-marketplace/${encodeURIComponent(listingId)}`
  return ({ dashboard: '/dashboard', products: '/stock', movements: '/movements', sync: '/sync', 'product-detail': '/stock', channels: '/channels', listings: '/listings', 'demo-marketplace': '/demo-marketplace' })[screen]
}

export default function App() {
  const [token, setToken] = useState(getToken())
  const [user, setUser] = useState<User | null>(null)
  const [data, setData] = useState(empty)
  const [screen, setScreen] = useState<Screen>(() => screenFromPath(window.location.pathname))
  const [routePath, setRoutePath] = useState(window.location.pathname)
  const [error, setError] = useState('')
  const [notificationOpen, setNotificationOpen] = useState(false)
  const [seenNotificationIds, setSeenNotificationIds] = useState<string[]>(() => {
    try { return JSON.parse(localStorage.getItem('stocksync.seenNotifications') || '[]') as string[] } catch { return [] }
  })
  const [dismissedNotificationIds, setDismissedNotificationIds] = useState<string[]>(() => {
    try { return JSON.parse(localStorage.getItem('stocksync.dismissedNotifications') || '[]') as string[] } catch { return [] }
  })
  const [loading, setLoading] = useState(false)
  const [query, setQuery] = useState('')
  const [detailProduct, setDetailProduct] = useState<Product | null>(null)
  const [movement, setMovement] = useState<'MANUAL_INCREASE' | 'MANUAL_DECREASE'>('MANUAL_INCREASE')
  const [movementQuantity, setMovementQuantity] = useState('1')
  const [editingProduct, setEditingProduct] = useState<Product | null>(null)
  const [creatingProduct, setCreatingProduct] = useState(false)
  const [savingNewProduct, setSavingNewProduct] = useState(false)
  const [movementProduct, setMovementProduct] = useState<Product | null>(null)
  const [savingProduct, setSavingProduct] = useState(false)
  const [savingMovement, setSavingMovement] = useState(false)
  const [savingStatuses, setSavingStatuses] = useState(false)
  const [channelEditor, setChannelEditor] = useState<SalesChannel | null | false>(false)
  const [savingChannel, setSavingChannel] = useState(false)
  const [listingEditor, setListingEditor] = useState(false)
  const [savingListing, setSavingListing] = useState(false)
  const [connectChannel, setConnectChannel] = useState<SalesChannel | null>(null)
  const [channelCredential, setChannelCredential] = useState<IntegrationCredential | null>(null)
  const [loadingCredential, setLoadingCredential] = useState(false)
  const [savingIntegration, setSavingIntegration] = useState(false)
  const [selectedIds, setSelectedIds] = useState<number[]>([])
  const selectAllRef = useRef<HTMLInputElement>(null)
  const [busyLogin, setBusyLogin] = useState(false)

  function navigateTo(nextScreen: Screen, productId?: number, listingId?: string) {
    setScreen(nextScreen)
    const nextPath = pathFromScreen(nextScreen, productId, listingId)
    setRoutePath(nextPath)
    if (window.location.pathname !== nextPath) window.history.pushState({}, '', nextPath)
  }

  useEffect(() => {
    const syncScreen = () => {
      const path = window.location.pathname
      setRoutePath(path)
      setScreen(screenFromPath(path))
    }
    window.addEventListener('popstate', syncScreen)
    return () => window.removeEventListener('popstate', syncScreen)
  }, [])
  useEffect(() => {
    const params = new URLSearchParams(window.location.search)
    const result = params.get('mercadolivre')
    if (!result) return
    if (result === 'connected') setError('Conta do Mercado Livre conectada com sucesso.')
    else if (result === 'denied') setError('A autorização do Mercado Livre foi cancelada.')
    else setError('Não foi possível concluir a autorização do Mercado Livre. Confira as credenciais e tente novamente.')
    window.history.replaceState({}, '', window.location.pathname)
    setRoutePath(window.location.pathname)
  }, [])
  useEffect(() => {
    const expireSession = () => {
      setToken(null)
      setUser(null)
      setError('Sua sessão expirou. Entre novamente para continuar.')
    }
    window.addEventListener('stocksync:unauthorized', expireSession)
    return () => window.removeEventListener('stocksync:unauthorized', expireSession)
  }, [])
  useEffect(() => {
    if (!error) return
    const timeout = window.setTimeout(() => setError(''), 4500)
    return () => window.clearTimeout(timeout)
  }, [error])

  const refresh = useCallback(async () => {
    if (!token) return
    setLoading(true)
    try {
      const products = (await api.products()).content
      const rows = await Promise.all(products.map(async p => [p.id, await api.inventory(p.id).catch(() => null)] as const))
      const stock = new Map<number, Inventory>(rows.filter((row): row is readonly [number, Inventory] => row[1] !== null))
      const histories = await Promise.all(products.map(async p => (await api.movements(p.id).catch(() => ({ content: [] }))).content))
      const syncs = (await api.failedSyncs().catch(() => ({ content: [] }))).content
      const [channels, listings] = await Promise.all([api.salesChannels(), api.listings()])
      let demoMode = false
      let demoListings: DemoMarketplaceListing[] = []
      try { demoListings = await api.demoMarketplaceListings(); demoMode = true }
      catch (cause) { if (!(cause instanceof ApiError) || cause.status !== 404) throw cause }
      setData({ products, stock, moves: histories.flat().sort((a,b) => Date.parse(b.occurredAt)-Date.parse(a.occurredAt)).slice(0, 10), syncs, channels: channels.content, listings: listings.content, demoListings, demoMode })
    } catch { setError('Não foi possível carregar os dados. Verifique sua sessão e a conexão com a API.')} finally { setLoading(false) }
  }, [token])
  useEffect(() => { void refresh() }, [refresh])
  useEffect(() => {
    if (screen !== 'product-detail') return
    const productId = Number(window.location.pathname.split('/')[2])
    const product = data.products.find(item => item.id === productId)
    if (product) setDetailProduct(product)
    else if (data.products.length && !loading) navigateTo('products')
  }, [screen, data.products, loading])
  const visibleProducts = useMemo(() => data.products.filter(p => `${p.name} ${p.sku}`.toLocaleLowerCase('pt-BR').includes(query.toLocaleLowerCase('pt-BR'))), [data.products, query])
  const selectedProducts = useMemo(() => data.products.filter(p => selectedIds.includes(p.id)), [data.products, selectedIds])
  const detailInventory = detailProduct ? data.stock.get(detailProduct.id) : undefined
  const selectedDemoListingId = screen === 'demo-marketplace' ? decodeURIComponent(routePath.split('/')[2] || '') : ''
  const selectedDemoListing = selectedDemoListingId ? data.demoListings.find(listing => listing.listingId === selectedDemoListingId) : undefined
  const detailMovements = detailProduct ? data.moves.filter(m => m.productId === detailProduct.id) : []
  const allVisibleSelected = visibleProducts.length > 0 && visibleProducts.every(p => selectedIds.includes(p.id))
  useEffect(() => { if (selectAllRef.current) selectAllRef.current.indeterminate = selectedIds.length > 0 && !allVisibleSelected }, [selectedIds, allVisibleSelected])
  const low = data.products.filter(p => p.active && (p.availableQuantity ?? 0) <= (data.stock.get(p.id)?.minQuantity ?? 0))
  const notifications = useMemo<AppNotification[]>(() => [
    ...low.map(product => ({ id: `stock-${product.id}-${product.availableQuantity}-${data.stock.get(product.id)?.minQuantity??0}`, kind: 'stock' as const, title: product.name, detail: `Estoque mínimo atingido · ${product.availableQuantity??0} disponíveis (mín. ${data.stock.get(product.id)?.minQuantity??0})`, productId: product.id })),
    ...data.syncs.map(event => ({ id: `sync-${event.id}`, kind: 'sync' as const, title: data.products.find(product => product.id === event.productId)?.name ?? `Produto #${event.productId??'—'}`, detail: event.errorMessage || `Falha na sincronização · ${date(event.occurredAt)}`, productId: event.productId ?? undefined })),
    ...data.moves.map(move => ({ id: `movement-${move.id}`, kind: 'movement' as const, title: data.products.find(product => product.id === move.productId)?.name ?? `Produto #${move.productId}`, detail: `${move.type==='MANUAL_INCREASE'?'Entrada':'Saída'} de ${move.quantity} un. · ${date(move.occurredAt)}`, productId: move.productId })),
  ], [low, data.syncs, data.moves, data.products])
  const activeNotifications = notifications.filter(item => !dismissedNotificationIds.includes(item.id))
  const unreadNotifications = activeNotifications.filter(item => !seenNotificationIds.includes(item.id))
  function markNotificationsSeen(ids: string[]) {
    setSeenNotificationIds(current => {
      const next = Array.from(new Set([...current, ...ids]))
      localStorage.setItem('stocksync.seenNotifications', JSON.stringify(next))
      return next
    })
  }
  function clearNotifications(ids: string[]) {
    setDismissedNotificationIds(current => {
      const next = Array.from(new Set([...current, ...ids]))
      localStorage.setItem('stocksync.dismissedNotifications', JSON.stringify(next))
      return next
    })
    markNotificationsSeen(ids)
  }
  async function submitLogin(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault(); setError(''); setBusyLogin(true)
    const form = new FormData(event.currentTarget)
    try { const result = await login(String(form.get('username')), String(form.get('password'))); setToken(result.accessToken); setUser(result.user) }
    catch (cause) { setError(getLoginError(cause)) } finally { setBusyLogin(false) }
  }
  async function submitProductCreate(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (savingNewProduct) return
    const form = new FormData(event.currentTarget)
    setSavingNewProduct(true)
    try {
      await api.createProduct({
        sku: String(form.get('sku')).trim(),
        name: String(form.get('name')).trim(),
        description: String(form.get('description') || '').trim(),
        basePrice: Number(form.get('basePrice')),
        initialQuantity: Number(form.get('initialQuantity')),
        minQuantity: Number(form.get('minQuantity')),
      })
      setCreatingProduct(false)
      setError('Produto cadastrado.')
      await refresh()
    } catch (cause) {
      const reason = cause instanceof ApiError
        ? [...cause.fieldErrors.map(item => item.message), cause.message].filter(Boolean).join(' · ')
        : 'Confira os dados e a conexão com a API.'
      setError(`Não foi possível cadastrar o produto. ${reason}`)
    } finally {
      setSavingNewProduct(false)
    }
  }
  async function submitChannel(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (channelEditor === false || savingChannel) return
    const form = new FormData(event.currentTarget)
    const channel = { name: String(form.get('name')).trim(), type: String(form.get('type')) as ChannelType, baseUrl: String(form.get('baseUrl') || '').trim() }
    setSavingChannel(true)
    try {
      if (channelEditor) await api.updateSalesChannel(channelEditor.id, channel)
      else await api.createSalesChannel(channel)
      setChannelEditor(false)
      setError(channelEditor ? 'Canal atualizado.' : 'Canal cadastrado.')
      await refresh()
    } catch (cause) {
      const reason = cause instanceof ApiError ? cause.message : 'Confira os dados e sua permissão.'
      setError(`Não foi possível salvar o canal. ${reason}`)
    } finally { setSavingChannel(false) }
  }
  async function toggleChannel(channel: SalesChannel) {
    try {
      await api.setSalesChannelActive(channel.id, !channel.active)
      setError(channel.active ? 'Canal desativado.' : 'Canal ativado.')
      await refresh()
    } catch (cause) {
      setError(`Não foi possível alterar o canal. ${cause instanceof ApiError ? cause.message : 'Verifique sua permissão.'}`)
    }
  }
  async function openMercadoLivreConnection(channel: SalesChannel) {
    setConnectChannel(channel)
    setChannelCredential(null)
    if (data.demoMode) return
    setLoadingCredential(true)
    try { setChannelCredential(await api.integrationCredential(channel.id)) }
    catch (cause) {
      if (!(cause instanceof ApiError) || cause.status !== 404) {
        setError(cause instanceof ApiError && cause.status === 403
          ? 'A configuração do Mercado Livre exige uma conta ADMIN.'
          : 'Não foi possível consultar as credenciais do canal.')
      }
    } finally { setLoadingCredential(false) }
  }
  async function submitMercadoLivreConnection(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!connectChannel || savingIntegration || data.demoMode) return
    const form = new FormData(event.currentTarget)
    setSavingIntegration(true)
    try {
      await api.saveIntegrationCredential(connectChannel.id, {
        clientId: String(form.get('clientId')).trim(),
        clientSecret: String(form.get('clientSecret')),
      })
      const { authorizationUrl } = await api.mercadoLivreAuthorizationUrl(connectChannel.id)
      window.location.assign(authorizationUrl)
    } catch (cause) {
      const reason = cause instanceof ApiError
        ? cause.status === 403 ? 'Esta operação exige uma conta ADMIN.' : cause.message
        : 'Verifique a API e a configuração OAuth.'
      setError(`Não foi possível iniciar a conexão com o Mercado Livre. ${reason}`)
      setSavingIntegration(false)
    }
  }
  async function submitListing(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (savingListing) return
    const form = new FormData(event.currentTarget)
    setSavingListing(true)
    try {
      await api.createListing({
        productId: Number(form.get('productId')),
        salesChannelId: Number(form.get('salesChannelId')),
        listingId: String(form.get('listingId')).trim(),
        listingUrl: String(form.get('listingUrl') || '').trim(),
        status: String(form.get('status')) as ListingStatus,
      })
      setListingEditor(false)
      setError('Anúncio vinculado.')
      await refresh()
    } catch (cause) {
      const reason = cause instanceof ApiError ? cause.message : 'Confira os dados e sua permissão.'
      setError(`Não foi possível vincular o anúncio. ${reason}`)
    } finally { setSavingListing(false) }
  }
  async function submitMovement(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (!detailProduct) return
    const form = new FormData(event.currentTarget)
    try { const quantity=Number(form.get('quantity')); await api.moveStock(detailProduct.id, quantity, movement, String(form.get('note') || '')); setMovementQuantity('1'); setDetailProduct(current=>current?{...current,availableQuantity:Math.max(0,(current.availableQuantity??0)+(movement==='MANUAL_INCREASE'?quantity:-quantity))}:current); await refresh() }
    catch { setError('Não foi possível registrar a movimentação.') }
  }
  async function submitStockMovement(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (!movementProduct || savingMovement) return
    const form = new FormData(event.currentTarget)
    const quantity = Number(form.get('quantity'))
    setSavingMovement(true)
    try {
      await api.moveStock(movementProduct.id, quantity, movement, String(form.get('note') || ''))
      setMovementProduct(null); setMovementQuantity('1'); setError('Movimentação registrada.'); await refresh()
    } catch { setError('Não foi possível registrar a movimentação. Confira a quantidade e sua permissão.') }
    finally { setSavingMovement(false) }
  }
  async function submitProductEdit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (!editingProduct) return
    const form = new FormData(event.currentTarget)
    setSavingProduct(true)
    try {
      const updatedProduct = await api.updateProduct(editingProduct.id, { sku: String(form.get('sku')), name: String(form.get('name')), description: String(form.get('description') || ''), basePrice: Number(form.get('basePrice')) })
      await api.updateMinQuantity(editingProduct.id, Number(form.get('minQuantity')))
      await api.updateAvailableQuantity(editingProduct.id, Number(form.get('availableQuantity')))
      setDetailProduct(current=>current?.id===editingProduct.id?{...current,...updatedProduct,availableQuantity:Number(form.get('availableQuantity'))}:current)
      setEditingProduct(null); setError('Produto atualizado.'); await refresh()
    } catch (cause) {
      const reason = cause instanceof ApiError ? (cause.status === 401 ? 'Sua sessão expirou. Entre novamente.' : cause.message) : 'Confira os dados e sua permissão.'
      setError(`Não foi possível atualizar o produto. ${reason}`)
    }
    finally { setSavingProduct(false) }
  }
  async function setSelectedProductsActive(active: boolean) {
    if (!selectedProducts.length || savingStatuses) return
    const changedProducts = selectedProducts.filter(product => product.active !== active)
    if (!changedProducts.length) { setError(active ? 'Os produtos selecionados já estão ativos.' : 'Os produtos selecionados já estão desativados.'); return }
    setSavingStatuses(true)
    try {
      const results = await Promise.allSettled(changedProducts.map(product => api.setProductActive(product.id, active)))
      const succeeded = results.filter(result => result.status === 'fulfilled').length
      const failed = results.length - succeeded
      setError(failed ? `${succeeded} produto(s) atualizado(s); ${failed} não puderam ser alterados.` : `${succeeded} produto(s) ${active ? 'ativado(s)' : 'desativado(s)'}.`)
      await refresh()
    } catch { setError('Não foi possível alterar o status dos produtos selecionados. Confira sua permissão.') }
    finally { setSavingStatuses(false) }
  }
  if (!token) return <main className="login-page"><form className="login-card" onSubmit={submitLogin}><div className="brand-mark">SS</div><p className="eyebrow">STOCKSYNC</p><h1>Acesse sua conta</h1><p className="login-copy">Entre para acompanhar seu estoque e seus anúncios.</p><label>Usuário<input name="username" autoComplete="username" placeholder="Ex.: demo" required/></label><label>Senha<input name="password" type="password" autoComplete="current-password" placeholder="Sua senha" required/></label>{error && <p className="form-error" role="alert">{error}</p>}<button className="primary" disabled={busyLogin}>{busyLogin ? 'Entrando…' : 'Entrar'}</button><p className="login-footnote">Gestão simples, estoque em dia.</p></form><div className="login-aside"><h2>Seu estoque,<br/>sempre em sincronia.</h2></div></main>
  const title = screen === 'product-detail' ? 'Produto' : ({ dashboard: 'Visão geral', products: 'Estoque', movements: 'Movimentações', sync: 'Sincronizações', channels: 'Canais de venda', listings: 'Anúncios', 'demo-marketplace': 'Mercado Livre simulado' })[screen]
  return <><main className="workspace"><aside className="sidebar"><div className="logo"><span className="logo-symbol"><Boxes size={18}/></span><b>Stock<span>Sync</span></b></div><div className="workspace-context"><span className="workspace-dot">S</span><span><b>Operações</b><small>Workspace principal</small></span><ChevronDown size={14}/></div><p>ESPAÇO DE TRABALHO</p><button className={screen==='dashboard'?'selected':''} onClick={()=>navigateTo('dashboard')}><LayoutDashboard size={17}/> Visão geral</button><button className={screen==='products'?'selected':''} onClick={()=>navigateTo('products')}><Package size={17}/> Estoque</button><button className={screen==='movements'?'selected':''} onClick={()=>navigateTo('movements')}><ArrowUpFromLine size={17}/> Movimentações</button><button className={screen==='sync'?'selected':''} onClick={()=>navigateTo('sync')}><Activity size={17}/> Sincronizações{data.syncs.length>0&&<span className="side-badge">{data.syncs.length}</span>}</button><button className={screen==='channels'?'selected':''} onClick={()=>navigateTo('channels')}><Store size={17}/> Canais de venda</button><button className={screen==='listings'?'selected':''} onClick={()=>navigateTo('listings')}><Link2 size={17}/> Anúncios</button>{data.demoMode&&<button className={screen==='demo-marketplace'?'selected':''} onClick={()=>navigateTo('demo-marketplace')}><Store size={17}/> Mercado Livre simulado</button>}<p className="side-section">PREFERÊNCIAS</p><button onClick={()=>setError('As configurações estarão disponíveis em uma próxima etapa.')}><Settings2 size={17}/> Configurações</button><footer><div className="account-avatar">{(user?.username??'D').slice(0,1).toUpperCase()}</div><span><b>{user?.username??'Operador'}</b><small>Operador</small></span><button aria-label="Sair" onClick={()=>{clearToken();setToken(null)}}><LogOut size={16}/></button></footer></aside><section className="main"><header><div className="crumb"><span>Workspace</span><ChevronRight size={14}/><strong>{title}</strong></div><div className="header-tools"><span className="api-indicator"><i/>API conectada</span><div className="notification-area"><button className={`notification-bell ${unreadNotifications.length?'has-notifications':''}`} aria-label={`Notificações${unreadNotifications.length?`, ${unreadNotifications.length} novas`:''}`} aria-expanded={notificationOpen} onClick={()=>{if(!notificationOpen)markNotificationsSeen(unreadNotifications.map(item=>item.id));setNotificationOpen(open=>!open)}}><Bell size={19}/>{unreadNotifications.length>0&&<i/>}</button>{notificationOpen&&<div className="notification-popover"><div className="notification-popover-title"><span>Notificações</span><div><button className="clear-notifications" disabled={!activeNotifications.length} onClick={()=>clearNotifications(activeNotifications.map(item=>item.id))}>Limpar notificações</button><button onClick={()=>setNotificationOpen(false)} aria-label="Fechar notificações"><X size={16}/></button></div></div>{activeNotifications.length?activeNotifications.map(item=><button className={`stock-alert-item ${item.kind}`} key={item.id} onClick={()=>{markNotificationsSeen([item.id]);if(item.kind==='sync')navigateTo('sync');else if(item.productId){const product=data.products.find(p=>p.id===item.productId);if(product)setDetailProduct(product);navigateTo('product-detail',item.productId)}setNotificationOpen(false)}}><span className="stock-alert-icon">{item.kind==='stock'?<AlertTriangle size={16}/>:item.kind==='sync'?<Activity size={16}/>:<ArrowUpFromLine size={16}/>}</span><span><b>{item.kind==='sync'?'Falha de sincronização':item.kind==='movement'?'Movimentação · ':''}{item.title}</b><small>{item.detail}</small></span><ChevronRight size={15}/></button>):<p className="no-notifications">Nenhuma notificação no momento.</p>}</div>}</div><button className="account-button" onClick={()=>{clearToken();setToken(null)}}>{(user?.username??'D').slice(0,1).toUpperCase()}</button></div></header><div className="page"><div className="page-title-row"><div><p className="date">INVENTÁRIO / VISÃO DO ESTOQUE</p><h1>{screen==='dashboard'?'Visão geral':screen==='product-detail'?(detailProduct?.name??'Produto'):title}</h1><p className="muted">{screen==='dashboard'?'Resumo operacional do estoque e das sincronizações.':screen==='products'?'Consulte quantidades, níveis mínimos e status dos produtos.':screen==='movements'?'Histórico auditável das entradas e saídas registradas.':screen==='product-detail'?'Detalhes e movimentações do produto selecionado.':screen==='channels'?'Configure os canais de venda disponíveis para os anúncios.':screen==='listings'?'Consulte os anúncios relacionados aos seus produtos.':screen==='demo-marketplace'?'Confira o estoque publicado no marketplace de demonstração.':'Eventos de sincronização que precisam de acompanhamento.'}</p></div><button className="refresh-button" onClick={()=>void refresh()} disabled={loading}>{loading?'Atualizando…':'Atualizar dados'}</button></div>
  {screen==='dashboard'&&<><div className="stats"><Stat label="Produtos ativos" value={data.products.filter(p=>p.active).length}/><Stat label="Unidades em estoque" value={data.products.reduce((n,p)=>n+(p.availableQuantity??0),0)}/><Stat label="Abaixo do mínimo" value={low.length}/><Stat label="Falhas de sincronização" value={data.syncs.length}/></div><div className="columns"><section className="card"><h2>Estoque baixo</h2>{low.length?low.map(p=><ProductLine key={p.id} product={p} stock={data.stock} onMove={p=>{setDetailProduct(p);navigateTo('product-detail',p.id)}}/>):<p className="muted">Nenhum produto abaixo do mínimo.</p>}</section><section className="card"><h2>Movimentações recentes</h2>{data.moves.slice(0,5).map(m=>{const product=data.products.find(p=>p.id===m.productId);return <div className="activity movement-activity" key={m.id}><span className="movement-summary"><b>{m.type==='MANUAL_INCREASE'?'Entrada':'Saída'}</b><span>{product?.name??`Produto #${m.productId}`}</span><small>{product?.sku??`ID ${m.productId}`}</small></span><b className="movement-amount">{m.type==='MANUAL_INCREASE'?'+':'−'}{m.quantity} un.</b></div>})}</section></div></>}
  {screen==='products'&&<section className="card stock-card"><div className="stock-toolbar"><div><h2>Lista de estoque</h2><p>{visibleProducts.length} itens encontrados{selectedProducts.length>0?` · ${selectedProducts.length} selecionados`:''}</p></div><div className="stock-controls"><label className="search-box"><Search size={16}/><input aria-label="Buscar produto" placeholder="Buscar produto ou SKU" value={query} onChange={e=>setQuery(e.target.value)}/><kbd>⌘ K</kbd></label><button className="primary create-product-button" onClick={()=>setCreatingProduct(true)}><Plus size={15}/> Novo produto</button><button className="status-action activate" disabled={!selectedProducts.length||savingStatuses} onClick={()=>void setSelectedProductsActive(true)}><Power size={15}/>{savingStatuses?'Atualizando…':`Ativar${selectedProducts.length?` (${selectedProducts.length})`:''}`}</button><button className="status-action deactivate" disabled={!selectedProducts.length||savingStatuses} onClick={()=>void setSelectedProductsActive(false)}><Power size={15}/>{savingStatuses?'Atualizando…':`Desativar${selectedProducts.length?` (${selectedProducts.length})`:''}`}</button></div></div><div className="table-scroll"><table className="stock-table"><thead><tr><th><input ref={selectAllRef} aria-label="Selecionar todos os itens visíveis" type="checkbox" checked={allVisibleSelected} onChange={e=>setSelectedIds(current=>e.target.checked?Array.from(new Set([...current,...visibleProducts.map(p=>p.id)])):current.filter(id=>!visibleProducts.some(p=>p.id===id)))} /></th><th>ITEM</th><th>SKU / REFERÊNCIA</th><th>DISPONÍVEL</th><th>ESTOQUE MÍNIMO</th><th>PREÇO BASE</th><th>STATUS</th><th>AÇÕES</th></tr></thead><tbody>{visibleProducts.map(p=>{const qty=p.availableQuantity??0;const min=data.stock.get(p.id)?.minQuantity??0;const isLow=p.active&&qty<=min;return <tr key={p.id} className={isLow?'row-low':''}><td><input type="checkbox" aria-label={`Selecionar ${p.name}`} checked={selectedIds.includes(p.id)} onChange={e=>setSelectedIds(current=>e.target.checked?[...current,p.id]:current.filter(id=>id!==p.id))}/></td><td><button className="item-link" onClick={()=>{setDetailProduct(p);navigateTo('product-detail',p.id)}}><span className="item-glyph"><Package size={16}/></span><span><b>{p.name}</b><small>{p.description||'Produto de inventário'}</small></span></button></td><td className="code-cell">{p.sku}</td><td><b className="tabular">{qty}</b><small className="unit-label">unidades</small></td><td><span className={isLow?'minimum-warning':''}>{min} un.</span>{isLow&&<small className="low-label"><AlertTriangle size={12}/> Reposição necessária</small>}</td><td className="tabular">{currency(p.basePrice)}</td><td><span className={`status-chip ${p.active?'on':'off'}`}><i/>{p.active?'Ativo':'Inativo'}</span></td><td><div className="product-actions"><button className="row-adjust" onClick={()=>{setEditingProduct(p);setError('')}} title="Editar produto"><Pencil size={14}/> Editar</button><button className="row-adjust" onClick={()=>{setMovementProduct(p);setMovement('MANUAL_INCREASE');setMovementQuantity('1');setError('')}} title="Registrar entrada ou saída"><ArrowDownToLine size={14}/> Movimentar</button></div></td></tr>})}</tbody></table>{!visibleProducts.length&&<div className="no-results"><b>Nenhum item encontrado</b><span>Limpe a busca ou procure pelo código SKU.</span></div>}</div><div className="table-summary"><span>Mostrando <b>{visibleProducts.length}</b> de <b>{data.products.length}</b> itens</span><span>Quantidades exibidas em unidades disponíveis</span></div></section>}
  {screen==='demo-marketplace'&&<section className="card domain-card"><div className="domain-card-head"><div><h2>{selectedDemoListing?.title??'Anúncios simulados'}</h2><p className="muted">Estado atual no adapter local do Mercado Livre.</p></div>{selectedDemoListing&&<button className="filter-button" onClick={()=>navigateTo('demo-marketplace')}>Todos os anúncios</button>}</div>{selectedDemoListing?<div className="demo-listing-detail"><div><span>Identificador externo</span><b className="code-cell">{selectedDemoListing.listingId}</b></div><div><span>Produto</span><b>{data.products.find(product=>product.id===selectedDemoListing.productId)?.name??`Produto #${selectedDemoListing.productId}`}</b></div><div><span>Estoque publicado</span><b>{selectedDemoListing.availableQuantity} unidades</b></div><div><span>Preço</span><b>{currency(selectedDemoListing.price)}</b></div><div><span>Status</span><b><span className={`status-chip ${selectedDemoListing.status==='ACTIVE'?'on':'off'}`}><i/>{listingStatusLabel(selectedDemoListing.status)}</span></b></div><div><span>Atualizado</span><b>{date(selectedDemoListing.updatedAt)}</b></div></div>:data.demoListings.length?<div className="table-scroll"><table><thead><tr><th>ANÚNCIO</th><th>PRODUTO</th><th>ESTOQUE PUBLICADO</th><th>PREÇO</th><th>STATUS</th><th>ATUALIZADO</th><th></th></tr></thead><tbody>{data.demoListings.map(item=><tr key={item.listingId}><td className="code-cell">{item.listingId}</td><td>{item.title}</td><td>{item.availableQuantity} un.</td><td>{currency(item.price)}</td><td><span className={`status-chip ${item.status==='ACTIVE'?'on':'off'}`}><i/>{listingStatusLabel(item.status)}</span></td><td>{date(item.updatedAt)}</td><td><button className="row-adjust" onClick={()=>navigateTo('demo-marketplace',undefined,item.listingId)}>Abrir detalhe</button></td></tr>)}</tbody></table></div>:<div className="no-results"><b>O simulador ainda não tem anúncios</b><span>Vincule um anúncio ao canal Mercado Livre para criá-lo no simulador.</span></div>}</section>}
  {screen==='channels'&&<section className="card domain-card"><div className="domain-card-head"><div><h2>Canais de venda</h2><p className="muted">Gerencie os marketplaces e canais conectados ao estoque.</p></div><button className="primary" onClick={()=>setChannelEditor(null)}><Plus size={15}/> Novo canal</button></div>{data.channels.length?<div className="table-scroll"><table><thead><tr><th>NOME</th><th>TIPO</th><th>URL BASE</th><th>STATUS</th><th>AÇÕES</th></tr></thead><tbody>{data.channels.map(channel=><tr key={channel.id}><td><b>{channel.name}</b></td><td>{channelTypeLabel(channel.type)}</td><td>{channel.baseUrl||'—'}</td><td><span className={`status-chip ${channel.active?'on':'off'}`}><i/>{channel.active?'Ativo':'Inativo'}</span></td><td><div className="product-actions"><button className="row-adjust" onClick={()=>setChannelEditor(channel)}><Pencil size={14}/> Editar</button><button className="row-adjust" onClick={()=>void toggleChannel(channel)}><Power size={14}/>{channel.active?'Desativar':'Ativar'}</button>{channel.type==='MERCADO_LIVRE'&&<button className="row-adjust" onClick={()=>void openMercadoLivreConnection(channel)}><Link2 size={14}/> Conectar Mercado Livre</button>}</div></td></tr>)}</tbody></table></div>:<div className="no-results"><b>Nenhum canal cadastrado</b><span>Cadastre um canal para começar a vincular anúncios.</span></div>}</section>}
  {screen==='listings'&&<section className="card domain-card"><div className="domain-card-head"><div><h2>Anúncios vinculados</h2><p className="muted">Relacionamento entre produto, canal e anúncio externo.</p></div><button className="primary" onClick={()=>setListingEditor(true)} disabled={!data.products.length||!data.channels.length}><Plus size={15}/> Vincular anúncio</button></div>{data.listings.length?<div className="table-scroll"><table><thead><tr><th>PRODUTO</th><th>CANAL</th><th>ID EXTERNO</th><th>STATUS</th><th>ÚLTIMA SINCRONIZAÇÃO</th><th>LINK</th></tr></thead><tbody>{data.listings.map(listing=>{const product=data.products.find(item=>item.id===listing.productId);const channel=data.channels.find(item=>item.id===listing.salesChannelId);return <tr key={listing.id}><td><b>{product?.name??`Produto #${listing.productId}`}</b><small className="domain-subline">{product?.sku??'Produto fora da página carregada'}</small></td><td>{channel?.name??`Canal #${listing.salesChannelId}`}</td><td className="code-cell">{listing.listingId}</td><td><span className={`status-chip ${listing.status==='ACTIVE'?'on':'off'}`}><i/>{listingStatusLabel(listing.status)}</span></td><td>{listing.lastSyncedAt?date(listing.lastSyncedAt):'Ainda não sincronizado'}</td><td>{data.demoMode?<button className="external-link demo-link-button" onClick={()=>navigateTo('demo-marketplace',undefined,listing.listingId)}>Ver simulação <ChevronRight size={13}/></button>:listing.listingUrl?<a className="external-link" href={listing.listingUrl} target="_blank" rel="noreferrer">Abrir anúncio <ChevronRight size={13}/></a>:'—'}</td></tr>})}</tbody></table></div>:<div className="no-results"><b>Nenhum anúncio vinculado</b><span>Crie um vínculo entre um produto e o anúncio no canal de venda.</span></div>}</section>}
  {screen==='movements'&&<section className="card movement-history"><div className="card-head"><div><h2>Histórico de movimentações</h2><p className="muted">Cada registro identifica o produto e a alteração de saldo.</p></div><button className="primary" onClick={()=>data.products[0]&&(setDetailProduct(data.products[0]),navigateTo('product-detail',data.products[0].id))}>Nova movimentação</button></div><div className="table-scroll"><table><thead><tr><th>TIPO</th><th>PRODUTO</th><th>SKU</th><th>QUANTIDADE</th><th>DATA</th><th>OBSERVAÇÃO</th></tr></thead><tbody>{data.moves.map(m=>{const product=data.products.find(p=>p.id===m.productId);const incoming=m.type==='MANUAL_INCREASE';return <tr key={m.id}><td><span className={`movement-kind ${incoming?'incoming':'outgoing'}`}>{incoming?'Entrada':'Saída'}</span></td><td><span className="movement-product">{product?.name??`Produto #${m.productId}`}</span></td><td className="code-cell">{product?.sku??`ID ${m.productId}`}</td><td className={incoming?'movement-in':'movement-out'}>{incoming?'+':'−'}{m.quantity} un.</td><td>{date(m.occurredAt)}</td><td>{m.note||'—'}</td></tr>})}</tbody></table>{!data.moves.length&&<div className="no-results"><b>Nenhuma movimentação registrada</b><span>As entradas e saídas aparecerão aqui com o produto correspondente.</span></div>}</div></section>}
  {screen==='sync'&&<section className="card"><h2>Falhas de sincronização</h2>{data.syncs.length?<table><thead><tr><th>PRODUTO</th><th>STATUS</th><th>DATA</th><th>TENTATIVAS</th><th>DETALHE</th></tr></thead><tbody>{data.syncs.map(s=><tr key={s.id}><td>{data.products.find(p=>p.id===s.productId)?.name??`Produto #${s.productId}`}</td><td>Falhou</td><td>{date(s.occurredAt)}</td><td>{s.attempts}</td><td>{s.errorMessage||'—'}</td></tr>)}</tbody></table>:<p className="muted">Nenhuma falha de sincronização.</p>}</section>}
  </div></section>
  {channelEditor!==false&&<div className="overlay" onMouseDown={e=>{if(e.target===e.currentTarget&&!savingChannel)setChannelEditor(false)}}><form className="dialog product-edit-dialog" onSubmit={submitChannel}><div className="review-top"><div><p className="date">CANAIS DE VENDA</p><h2>{channelEditor?'Editar canal':'Novo canal'}</h2></div><button type="button" className="close" onClick={()=>setChannelEditor(false)} aria-label="Fechar" disabled={savingChannel}><X size={19}/></button></div><label className="edit-field">Nome do canal<input name="name" maxLength={100} defaultValue={channelEditor&&channelEditor.name?channelEditor.name:''} required/></label><label className="edit-field">Tipo<select name="type" defaultValue={channelEditor&&channelEditor.type?channelEditor.type:'MERCADO_LIVRE'} required><option value="MERCADO_LIVRE">Mercado Livre</option><option value="SHOPEE">Shopee</option><option value="OTHER">Outro</option></select></label><label className="edit-field">URL base <span className="optional-label">opcional</span><input name="baseUrl" type="url" maxLength={500} defaultValue={channelEditor&&channelEditor.baseUrl?channelEditor.baseUrl:''} placeholder="https://…"/></label><div className="review-actions"><button type="button" className="filter-button" onClick={()=>setChannelEditor(false)} disabled={savingChannel}>Cancelar</button><button className="primary" disabled={savingChannel}>{savingChannel?'Salvando…':channelEditor?'Salvar alterações':'Cadastrar canal'}</button></div></form></div>}
  {connectChannel&&<div className="overlay" onMouseDown={e=>{if(e.target===e.currentTarget&&!savingIntegration)setConnectChannel(null)}}><form className="dialog product-edit-dialog" onSubmit={submitMercadoLivreConnection}><div className="review-top"><div><p className="date">INTEGRAÇÃO / MERCADO LIVRE</p><h2>Conectar {connectChannel.name}</h2></div><button type="button" className="close" onClick={()=>setConnectChannel(null)} aria-label="Fechar" disabled={savingIntegration}><X size={19}/></button></div>{data.demoMode?<div className="connection-warning"><b>O backend está no modo de demonstração.</b><span>A conexão real só fica disponível com a API iniciada sem o profile <code>demo</code> e com a URL de callback cadastrada no aplicativo do Mercado Livre.</span></div>:<><p className="muted connection-help">Use as credenciais do aplicativo criado no painel de desenvolvedores do Mercado Livre. O segredo é armazenado criptografado pelo backend.</p>{loadingCredential?<p className="muted">Consultando conexão…</p>:channelCredential&&<p className="credential-state"><span className={`status-chip ${channelCredential.connected?'on':'off'}`}><i/>{channelCredential.connected?'Conta conectada':'Ainda não autorizado'}</span>{channelCredential.connected&&channelCredential.expiresAt?` · expira ${date(channelCredential.expiresAt)}`:''}</p>}<label className="edit-field">Client ID<input name="clientId" autoComplete="off" defaultValue={channelCredential?.clientId??''} required disabled={loadingCredential}/></label><label className="edit-field">Client Secret<input name="clientSecret" type="password" autoComplete="new-password" required disabled={loadingCredential}/><small className="field-help">Digite novamente o segredo do app para conectar ou renovar a autorização.</small></label><p className="connection-help">Você precisa entrar como ADMIN. Depois de salvar, o Mercado Livre abrirá para você autorizar a conta.</p><div className="review-actions"><button type="button" className="filter-button" onClick={()=>setConnectChannel(null)} disabled={savingIntegration}>Cancelar</button><button className="primary" disabled={savingIntegration||loadingCredential}>{savingIntegration?'Salvando e abrindo…':channelCredential?.connected?'Reconectar Mercado Livre':'Salvar e conectar'}</button></div></>}</form></div>}
  {listingEditor&&<div className="overlay" onMouseDown={e=>{if(e.target===e.currentTarget&&!savingListing)setListingEditor(false)}}><form className="dialog product-edit-dialog" onSubmit={submitListing}><div className="review-top"><div><p className="date">ANÚNCIOS / VÍNCULO</p><h2>Vincular anúncio</h2></div><button type="button" className="close" onClick={()=>setListingEditor(false)} aria-label="Fechar" disabled={savingListing}><X size={19}/></button></div><label className="edit-field">Produto<select name="productId" required defaultValue=""><option value="" disabled>Selecione o produto</option>{data.products.map(product=><option value={product.id} key={product.id}>{product.name} · {product.sku}</option>)}</select></label><label className="edit-field">Canal de venda<select name="salesChannelId" required defaultValue=""><option value="" disabled>Selecione o canal</option>{data.channels.filter(channel=>channel.active).map(channel=><option value={channel.id} key={channel.id}>{channel.name}</option>)}</select></label><label className="edit-field">ID do anúncio<input name="listingId" maxLength={255} required/></label><label className="edit-field">URL do anúncio <span className="optional-label">opcional</span><input name="listingUrl" type="url" maxLength={1000} placeholder="https://…"/></label><label className="edit-field">Status inicial<select name="status" defaultValue="ACTIVE"><option value="ACTIVE">Ativo</option><option value="INACTIVE">Inativo</option><option value="PAUSED">Pausado</option><option value="CLOSED">Encerrado</option></select></label><div className="review-actions"><button type="button" className="filter-button" onClick={()=>setListingEditor(false)} disabled={savingListing}>Cancelar</button><button className="primary" disabled={savingListing}>{savingListing?'Vinculando…':'Vincular anúncio'}</button></div></form></div>}
  {creatingProduct&&<div className="overlay" onMouseDown={e=>{if(e.target===e.currentTarget&&!savingNewProduct)setCreatingProduct(false)}}><form className="dialog product-edit-dialog" onSubmit={submitProductCreate}><div className="review-top"><div><p className="date">CADASTRO DO PRODUTO</p><h2>Novo produto</h2></div><button type="button" className="close" onClick={()=>setCreatingProduct(false)} aria-label="Fechar" disabled={savingNewProduct}><X size={19}/></button></div><label className="edit-field">SKU<input name="sku" maxLength={100} required/></label><label className="edit-field">Nome do produto<input name="name" minLength={3} maxLength={200} required/></label><label className="edit-field">Descrição<input name="description" maxLength={1000}/></label><label className="edit-field">Preço base (R$)<input name="basePrice" type="number" min="0.01" step="0.01" required/></label><label className="edit-field">Quantidade inicial<input name="initialQuantity" type="number" min="0" step="1" defaultValue="0" required/></label><label className="edit-field">Estoque mínimo<input name="minQuantity" type="number" min="0" step="1" defaultValue="0" required/><small className="field-help">Será usado para sinalizar necessidade de reposição.</small></label><div className="review-actions"><button type="button" className="filter-button" onClick={()=>setCreatingProduct(false)} disabled={savingNewProduct}>Cancelar</button><button className="primary" disabled={savingNewProduct}>{savingNewProduct?'Cadastrando…':'Cadastrar produto'}</button></div></form></div>}
  {editingProduct&&<div className="overlay" onMouseDown={e=>{if(e.target===e.currentTarget)setEditingProduct(null)}}><form className="dialog product-edit-dialog" onSubmit={submitProductEdit}><div className="review-top"><div><p className="date">CADASTRO DO PRODUTO</p><h2>Editar produto</h2></div><button type="button" className="close" onClick={()=>setEditingProduct(null)} aria-label="Fechar"><X size={19}/></button></div><div className="edit-product-reference"><span className="item-glyph"><Package size={18}/></span><span><b>{editingProduct.sku}</b><small>Identificação do produto</small></span></div><label className="edit-field">SKU<input name="sku" defaultValue={editingProduct.sku} maxLength={100} required/></label><label className="edit-field">Nome do produto<input name="name" defaultValue={editingProduct.name} minLength={3} maxLength={200} required/></label><label className="edit-field">Descrição<input name="description" defaultValue={editingProduct.description??''} maxLength={1000}/></label><label className="edit-field">Preço base (R$)<input name="basePrice" type="number" min="0" step="0.01" defaultValue={editingProduct.basePrice} required/></label><label className="edit-field">Quantidade em estoque<input name="availableQuantity" type="number" min="0" step="1" defaultValue={editingProduct.availableQuantity??0} required/></label><label className="edit-field">Estoque mínimo<input name="minQuantity" type="number" min="0" step="1" defaultValue={data.stock.get(editingProduct.id)?.minQuantity??0} required/><small className="field-help">Ajuste a quantidade mínima para alerta de reposição.</small></label><div className="review-actions"><button type="button" className="filter-button" onClick={()=>setEditingProduct(null)}>Cancelar</button><button className="primary" disabled={savingProduct}>{savingProduct?'Salvando…':'Salvar alterações'}</button></div></form></div>}
  {movementProduct&&<div className="overlay" onMouseDown={e=>{if(e.target===e.currentTarget)setMovementProduct(null)}}><form className="dialog product-edit-dialog" onSubmit={submitStockMovement}><div className="review-top"><div><p className="date">ESTOQUE / MOVIMENTAÇÃO</p><h2>Registrar movimentação</h2></div><button type="button" className="close" onClick={()=>setMovementProduct(null)} aria-label="Fechar"><X size={19}/></button></div><div className="edit-product-reference movement-product-reference"><span className="item-glyph"><Package size={18}/></span><span className="movement-product-name"><b>{movementProduct.name}</b><small>{movementProduct.sku}</small></span><span className="current-stock"><small>Saldo atual</small><b>{movementProduct.availableQuantity??0}<small>un.</small></b></span></div><label className="edit-field">Tipo de movimentação<select value={movement} onChange={e=>setMovement(e.target.value as typeof movement)}><option value="MANUAL_INCREASE">Entrada de estoque</option><option value="MANUAL_DECREASE">Saída de estoque</option></select></label><label className="edit-field">Quantidade<input name="quantity" type="number" min="1" max={movement==='MANUAL_DECREASE'?(movementProduct.availableQuantity??0):undefined} value={movementQuantity} onChange={e=>setMovementQuantity(e.target.value)} required/></label><div className="detail-result"><span>Saldo após movimentação</span><b>{Math.max(0,(movementProduct.availableQuantity??0)+(movement==='MANUAL_INCREASE'?1:-1)*Number(movementQuantity||0))}<small>unidades</small></b></div><label className="edit-field"><span>Motivo / observação <span className="optional-label">opcional</span></span><input name="note" maxLength={255} placeholder="Informe o motivo"/></label><div className="review-actions"><button type="button" className="filter-button" onClick={()=>setMovementProduct(null)}>Cancelar</button><button className="primary" disabled={savingMovement}>{savingMovement?'Registrando…':'Confirmar movimentação'}</button></div></form></div>}
  {screen==='product-detail'&&detailProduct&&<section className="product-detail-page"><button className="back-link" onClick={()=>{navigateTo('products');setSelectedIds([])}}>← Voltar ao estoque</button><div className="detail-heading"><div className="detail-heading-info"><p className="date">DETALHE DO PRODUTO</p><h2>{detailProduct.name}</h2><p>{detailProduct.description||'Sem descrição cadastrada.'}</p></div><div className="detail-heading-actions"><button className="row-adjust" onClick={()=>{setEditingProduct(detailProduct);setError('')}}><Pencil size={14}/> Editar produto</button><span className={`status-chip ${detailProduct.active?'on':'off'}`}><i/>{detailProduct.active?'Ativo':'Inativo'}</span></div></div><div className="detail-stats"><article><span>SKU</span><b className="detail-sku">{detailProduct.sku}</b></article><article><span>Disponível</span><b>{detailProduct.availableQuantity??0}<small>un.</small></b></article><article><span>Estoque mínimo</span><b>{detailInventory?.minQuantity??0}<small>un.</small></b></article><article><span>Preço base</span><b>{currency(detailProduct.basePrice)}</b></article></div><div className="detail-columns"><section className="card detail-adjust-card"><p className="date">MOVIMENTAÇÃO</p><h3>Ajustar este produto</h3><p className="muted">O ajuste será aplicado somente a este produto.</p><form onSubmit={submitMovement}><label className="edit-field">Tipo de ajuste<select value={movement} onChange={e=>setMovement(e.target.value as typeof movement)}><option value="MANUAL_INCREASE">Entrada de estoque</option><option value="MANUAL_DECREASE">Saída de estoque</option></select></label><label className="edit-field">Quantidade<input name="quantity" type="number" min="1" value={movementQuantity} onChange={e=>setMovementQuantity(e.target.value)} required/></label><div className="detail-result"><span>Saldo após ajuste</span><b>{Math.max(0,(detailProduct.availableQuantity??0)+(movement==='MANUAL_INCREASE'?1:-1)*Number(movementQuantity||0))} <small>unidades</small></b></div><label className="edit-field">Motivo / observação <span className="optional-label">opcional</span><input name="note" maxLength={255} placeholder="Informe o motivo do ajuste"/></label><button className="primary detail-submit" disabled={loading}>{loading?'Registrando…':'Confirmar movimentação'}</button></form></section><section className="card detail-history"><p className="date">RASTREABILIDADE</p><h3>Movimentações recentes</h3>{detailMovements.length?detailMovements.map(item=><div className="detail-movement-row" key={item.id}><span className={`movement-kind ${item.type==='MANUAL_INCREASE'?'incoming':'outgoing'}`}>{item.type==='MANUAL_INCREASE'?'Entrada':'Saída'}</span><b className={item.type==='MANUAL_INCREASE'?'movement-in':'movement-out'}>{item.type==='MANUAL_INCREASE'?'+':'−'}{item.quantity} un.</b><time>{date(item.occurredAt)}</time><small>{item.note||'Sem observação'}</small></div>):<div className="no-results"><b>Sem movimentações</b><span>O histórico deste produto aparecerá aqui.</span></div>}</section></div></section>}</main>{error&&<div className={`toast-notification ${error.includes('Não foi possível')?'error':''}`} role="status"><span className="toast-icon">{error.includes('Não foi possível')?'!':'✓'}</span><span>{error}</span><button onClick={()=>setError('')} aria-label="Fechar notificação"><X size={17}/></button></div>}</>
}
function channelTypeLabel(type: ChannelType){return ({MERCADO_LIVRE:'Mercado Livre',SHOPEE:'Shopee',OTHER:'Outro'})[type]}
function listingStatusLabel(status: ListingStatus){return ({ACTIVE:'Ativo',INACTIVE:'Inativo',PAUSED:'Pausado',CLOSED:'Encerrado'})[status]}
function Stat({label,value}:{label:string;value:number}){return <article className="stat"><span>{label}</span><b>{value.toLocaleString('pt-BR')}</b></article>}
function ProductLine({product,stock,onMove}:{product:Product;stock:Map<number,Inventory>;onMove:(p:Product)=>void}){return <div className="product-line"><span>{product.name}<small>{product.sku}</small></span><b>{product.availableQuantity??0} / {stock.get(product.id)?.minQuantity??0}</b><button onClick={()=>onMove(product)}>Repor</button></div>}
function currency(n:number){return new Intl.NumberFormat('pt-BR',{style:'currency',currency:'BRL'}).format(n??0)}
function date(s:string){return new Intl.DateTimeFormat('pt-BR',{dateStyle:'short',timeStyle:'short'}).format(new Date(s))}

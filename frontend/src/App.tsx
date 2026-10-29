import React, { useCallback, useEffect, useMemo, useState } from 'react'
import { login, clearToken, getToken, api } from './api'
import type { User, Product, Inventory, StockMovement, SyncEvent } from './types'

type Screen = 'dashboard' | 'products' | 'movements' | 'sync'
type Data = { products: Product[]; stock: Map<number, Inventory>; moves: StockMovement[]; syncs: SyncEvent[] }
const empty: Data = { products: [], stock: new Map(), moves: [], syncs: [] }

export default function App() {
  const [token, setToken] = useState(getToken())
  const [user, setUser] = useState<User | null>(null)
  const [data, setData] = useState(empty)
  const [screen, setScreen] = useState<Screen>('dashboard')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const [query, setQuery] = useState('')
  const [selected, setSelected] = useState<Product | null>(null)
  const [movement, setMovement] = useState<'MANUAL_INCREASE' | 'MANUAL_DECREASE'>('MANUAL_INCREASE')
  const [busyLogin, setBusyLogin] = useState(false)

  const refresh = useCallback(async () => {
    if (!token) return
    setLoading(true)
    try {
      const products = (await api.products()).content
      const rows = await Promise.all(products.map(async p => [p.id, await api.inventory(p.id).catch(() => null)] as const))
      const stock = new Map<number, Inventory>(rows.filter((row): row is readonly [number, Inventory] => row[1] !== null))
      const histories = await Promise.all(products.map(async p => (await api.movements(p.id).catch(() => ({ content: [] }))).content))
      const syncs = (await api.failedSyncs().catch(() => ({ content: [] }))).content
      setData({ products, stock, moves: histories.flat().sort((a,b) => Date.parse(b.occurredAt)-Date.parse(a.occurredAt)).slice(0, 10), syncs })
    } catch { setError('Não foi possível carregar os dados. Verifique sua sessão e a conexão com a API.')} finally { setLoading(false) }
  }, [token])
  useEffect(() => { void refresh() }, [refresh])
  const visibleProducts = useMemo(() => data.products.filter(p => `${p.name} ${p.sku}`.toLocaleLowerCase('pt-BR').includes(query.toLocaleLowerCase('pt-BR'))), [data.products, query])
  async function submitLogin(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault(); setError(''); setBusyLogin(true)
    const form = new FormData(event.currentTarget)
    try { const result = await login(String(form.get('username')), String(form.get('password'))); setToken(result.accessToken); setUser(result.user) }
    catch { setError('Usuário ou senha inválidos.') } finally { setBusyLogin(false) }
  }
  async function submitMovement(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (!selected) return
    const form = new FormData(event.currentTarget)
    try { await api.moveStock(selected.id, Number(form.get('quantity')), movement, String(form.get('note') || '')); setSelected(null); await refresh() }
    catch { setError('Não foi possível registrar a movimentação.') }
  }
  if (!token) return <main className="login-page"><form className="login-card" onSubmit={submitLogin}><div className="brand-mark">SS</div><p className="eyebrow">STOCKSYNC</p><h1>Acesse sua conta</h1><p className="login-copy">Entre para acompanhar seu estoque e seus anúncios.</p><label>Usuário<input name="username" autoComplete="username" required/></label><label>Senha<input name="password" type="password" autoComplete="current-password" required/></label>{error && <p className="form-error">{error}</p>}<button className="primary" disabled={busyLogin}>{busyLogin ? 'Entrando…' : 'Entrar'}</button><p className="login-footnote">Gestão simples, estoque em dia.</p></form><div className="login-aside"><h2>Seu estoque,<br/>sempre em sincronia.</h2></div></main>
  const low = data.products.filter(p => p.active && (p.availableQuantity ?? 0) <= (data.stock.get(p.id)?.minQuantity ?? 0))
  const title = ({ dashboard: 'Visão geral', products: 'Produtos', movements: 'Movimentações', sync: 'Sincronizações' })[screen]
  return <main className="workspace"><aside className="sidebar"><div className="logo">▦ <b>stock<span>sync</span></b></div><p>MENU PRINCIPAL</p>{(['dashboard','products','movements','sync'] as Screen[]).map(s=><button className={screen===s?'selected':''} onClick={()=>setScreen(s)} key={s}>{({dashboard:'◫  Visão geral',products:'□  Produtos',movements:'↕  Movimentações',sync:'◉  Sincronizações'})[s]}</button>)}<footer><span>{user?.username ?? 'Operador'}</span><button onClick={()=>{clearToken();setToken(null)}}>Sair</button></footer></aside><section className="main"><header><span>StockSync　/　{title}</span><span className="user">{user?.username ?? 'demo'}</span></header><div className="page"><p className="date">GESTÃO DE ESTOQUE</p><h1>{screen==='dashboard'?'Bom dia 👋':title}</h1><p className="muted">{screen==='dashboard'?'Aqui está o resumo do seu estoque hoje.':'Consulte e gerencie as informações do seu estoque.'}</p>{error&&<p className="form-error">{error}</p>}
  {screen==='dashboard'&&<><div className="stats"><Stat label="Produtos ativos" value={data.products.filter(p=>p.active).length}/><Stat label="Unidades em estoque" value={data.products.reduce((n,p)=>n+(p.availableQuantity??0),0)}/><Stat label="Abaixo do mínimo" value={low.length}/><Stat label="Falhas de sincronização" value={data.syncs.length}/></div><div className="columns"><section className="card"><h2>Estoque baixo</h2>{low.length?low.map(p=><ProductLine key={p.id} product={p} stock={data.stock} onMove={setSelected}/>):<p className="muted">Nenhum produto abaixo do mínimo.</p>}</section><section className="card"><h2>Movimentações recentes</h2>{data.moves.slice(0,5).map(m=><p className="activity" key={m.id}>{m.type==='MANUAL_INCREASE'?'Entrada':'Saída'} · {data.products.find(p=>p.id===m.productId)?.name ?? `Produto #${m.productId}`} <b>{m.quantity} un.</b></p>)}</section></div></>}
  {screen==='products'&&<section className="card"><div className="toolbar"><input placeholder="Buscar por nome ou SKU" value={query} onChange={e=>setQuery(e.target.value)}/><button className="primary" onClick={()=>data.products[0]&&setSelected(data.products[0])}>Movimentar estoque</button></div><table><thead><tr><th>PRODUTO</th><th>SKU</th><th>ESTOQUE</th><th>MÍNIMO</th><th>PREÇO BASE</th><th>STATUS</th><th></th></tr></thead><tbody>{visibleProducts.map(p=><tr key={p.id}><td>{p.name}</td><td>{p.sku}</td><td>{p.availableQuantity??0}</td><td>{data.stock.get(p.id)?.minQuantity??0}</td><td>{currency(p.basePrice)}</td><td>{p.active?'Ativo':'Inativo'}</td><td><button onClick={()=>setSelected(p)}>Ajustar</button></td></tr>)}</tbody></table></section>}
  {screen==='movements'&&<section className="card"><div className="card-head"><h2>Histórico de movimentações</h2><button className="primary" onClick={()=>data.products[0]&&setSelected(data.products[0])}>Nova movimentação</button></div><table><thead><tr><th>TIPO</th><th>PRODUTO</th><th>QUANTIDADE</th><th>DATA</th><th>OBSERVAÇÃO</th></tr></thead><tbody>{data.moves.map(m=><tr key={m.id}><td>{m.type==='MANUAL_INCREASE'?'Entrada':'Saída'}</td><td>{data.products.find(p=>p.id===m.productId)?.name??`Produto #${m.productId}`}</td><td>{m.quantity}</td><td>{date(m.occurredAt)}</td><td>{m.note||'—'}</td></tr>)}</tbody></table></section>}
  {screen==='sync'&&<section className="card"><h2>Falhas de sincronização</h2>{data.syncs.length?<table><thead><tr><th>PRODUTO</th><th>STATUS</th><th>DATA</th><th>TENTATIVAS</th><th>DETALHE</th></tr></thead><tbody>{data.syncs.map(s=><tr key={s.id}><td>{data.products.find(p=>p.id===s.productId)?.name??`Produto #${s.productId}`}</td><td>Falhou</td><td>{date(s.occurredAt)}</td><td>{s.attempts}</td><td>{s.errorMessage||'—'}</td></tr>)}</tbody></table>:<p className="muted">Nenhuma falha de sincronização.</p>}</section>}
  <button className="refresh" onClick={()=>void refresh()} disabled={loading}>{loading?'Atualizando…':'Atualizar dados'}</button></div></section>{selected&&<div className="overlay"><form className="dialog" onSubmit={submitMovement}><button type="button" className="close" onClick={()=>setSelected(null)}>×</button><p className="date">{selected.sku}</p><h2>Movimentar estoque</h2><p>{selected.name}</p><label>Tipo<select value={movement} onChange={e=>setMovement(e.target.value as typeof movement)}><option value="MANUAL_INCREASE">Entrada</option><option value="MANUAL_DECREASE">Saída</option></select></label><label>Quantidade<input name="quantity" type="number" min="1" defaultValue="1" required/></label><label>Observação<input name="note" maxLength={255}/></label><button className="primary">Registrar</button></form></div>}</main>
}
function Stat({label,value}:{label:string;value:number}){return <article className="stat"><span>{label}</span><b>{value.toLocaleString('pt-BR')}</b></article>}
function ProductLine({product,stock,onMove}:{product:Product;stock:Map<number,Inventory>;onMove:(p:Product)=>void}){return <div className="product-line"><span>{product.name}<small>{product.sku}</small></span><b>{product.availableQuantity??0} / {stock.get(product.id)?.minQuantity??0}</b><button onClick={()=>onMove(product)}>Repor</button></div>}
function currency(n:number){return new Intl.NumberFormat('pt-BR',{style:'currency',currency:'BRL'}).format(n??0)}
function date(s:string){return new Intl.DateTimeFormat('pt-BR',{dateStyle:'short',timeStyle:'short'}).format(new Date(s))}

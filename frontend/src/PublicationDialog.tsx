import { useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { X } from 'lucide-react'
import { api, ApiError } from './api'
import type { Product, SalesChannel, PublicationField, PublicationOptions, PublicationRequest } from './types'

type Review = { request: PublicationRequest; quantity: number; productName: string; channelName: string; categoryName: string; listingTypeName: string; warnings?: string[] }
const PENDING_KEY = 'stocksync.pendingPublication'

function pendingReview(): Review | null {
  try { return JSON.parse(sessionStorage.getItem(PENDING_KEY) || 'null') as Review | null } catch { return null }
}

function warningMessage(issue: string) {
  if (issue.startsWith('shipping.me2_adoption_mandatory')) return 'O Mercado Livre exige Mercado Envios (ME2) e poderá ajustar a modalidade informada. As regras e custos desse envio se aplicam.'
  if (issue.startsWith('shipping.lost_me1_by_user')) return 'Esta conta não está habilitada para a modalidade de envio ME1.'
  return issue
}

function errorMessage(cause: unknown): string {
  if (!(cause instanceof ApiError)) return 'Não foi possível acessar a API. Confira sua conexão.'
  if (cause.status === 403) return 'Sua conta precisa ter permissão de administrador ou operador.'
  if (cause.status === 409) return `Confira a autorização do canal ou uma tentativa de publicação pendente. ${cause.message}`
  if (cause.issues.length) return `O Mercado Livre exige correções:\n${cause.issues.map(issue => {
    if (issue.startsWith('shipping.me2_adoption_mandatory')) return '• Esta conta exige Mercado Envios. Selecione essa opção no campo Envio.'
    if (issue.startsWith('shipping.lost_me1_by_user')) return '• A modalidade de envio ME1 não está habilitada para esta conta.'
    return `• ${issue}`
  }).join('\n')}`
  return [...cause.fieldErrors.map(e => e.message), cause.message].filter(Boolean).join(' · ')
}

function Field({ field, prefix, initial }: { field: PublicationField; prefix: string; initial?: string }) {
  const name = `${prefix}${field.id}`
  const restricted = field.valueType === 'list' || field.valueType === 'boolean'
  return <label className="edit-field">{field.name}{field.required ? ' *' : <span className="optional-label"> opcional</span>}
    {restricted && field.values.length ? <select name={name} required={field.required} defaultValue={initial || ''}>
      <option value="">Selecione</option>{field.values.map(v => <option key={v.id} value={v.name}>{v.name}</option>)}
    </select> : <><input name={name} defaultValue={initial || ''} required={field.required} maxLength={255} list={`${name}-values`} placeholder={field.units.length ? `Ex.: 10 ${field.units[0]}` : undefined}/>
      {field.values.length > 0 && <datalist id={`${name}-values`}>{field.values.map(v => <option key={v.id} value={v.name}/>)}</datalist>}</>}
    {(field.hint || field.units.length > 0) && <small className="field-help">{field.hint} {field.units.length > 0 && `Unidades: ${field.units.join(', ')}`}</small>}
  </label>
}

function fieldsFromForm(form: FormData, fields: PublicationField[], prefix: string) {
  return fields.flatMap(field => {
    const value = String(form.get(`${prefix}${field.id}`) || '').trim()
    if (!value) return []
    const match = field.values.find(v => v.name === value)
    return [{ id: field.id, ...(match ? { valueId: match.id } : { valueName: value }) }]
  })
}

export default function PublicationDialog({ products, channels, onClose, onPublished }: {
  products: Product[]; channels: SalesChannel[]; onClose: () => void; onPublished: (message: string) => void
}) {
  const [productId, setProductId] = useState('')
  const [channelId, setChannelId] = useState('')
  const [name, setName] = useState('')
  const [price, setPrice] = useState('')
  const [description, setDescription] = useState('')
  const [query, setQuery] = useState('')
  const [categories, setCategories] = useState<{ id: string; name: string }[]>([])
  const [categoryId, setCategoryId] = useState('')
  const [options, setOptions] = useState<PublicationOptions | null>(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [review, setReview] = useState<Review | null>(pendingReview)
  const [draft, setDraft] = useState<PublicationRequest | null>(null)
  const [pending, setPending] = useState(() => pendingReview() !== null)
  const sequence = useRef(0)
  const product = products.find(p => p.id === Number(productId))

  async function search() {
    if (!channelId || !query.trim() || busy) return
    const current = ++sequence.current
    setBusy(true); setError(''); setOptions(null); setDraft(null); setCategoryId('')
    try {
      const result = await api.publicationCategories(Number(channelId), query.trim())
      if (current === sequence.current) { setCategories(result); if (!result.length) setError('Nenhuma categoria encontrada. Tente uma descrição mais específica.') }
    } catch (cause) { if (current === sequence.current) setError(errorMessage(cause)) }
    finally { setBusy(false) }
  }

  async function chooseCategory(id: string) {
    const current = ++sequence.current
    setBusy(true); setError(''); setOptions(null); setDraft(null)
    try { const result = await api.publicationOptions(Number(channelId), id); if (current === sequence.current) setOptions(result) }
    catch (cause) { if (current === sequence.current) setError(errorMessage(cause)) }
    finally { setBusy(false) }
  }

  async function prepare(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!options || !product || busy) return
    const form = new FormData(event.currentTarget)
    const pictures = String(form.get('pictures') || '').split(/\r?\n/).map(s => s.trim()).filter(Boolean)
    if (!pictures.length || pictures.length > options.maxPictures || pictures.some(url => { try { return new URL(url).protocol !== 'https:' } catch { return true } })) {
      setError(`Informe de 1 a ${options.maxPictures} fotos com URLs HTTPS públicas, uma por linha.`); return
    }
    const request: PublicationRequest = {
      requestId: crypto.randomUUID(), productId: product.id, salesChannelId: Number(channelId), name: name.trim(),
      categoryId: options.categoryId, price: Number(price), listingTypeId: String(form.get('listingTypeId')),
      pictures, attributes: fieldsFromForm(form, options.attributes, 'attr_'), saleTerms: fieldsFromForm(form, options.saleTerms, 'term_'),
      shippingMode: String(form.get('shippingMode')), freeShipping: form.has('freeShipping'), localPickUp: form.has('localPickUp'), description: description.trim(),
    }
    setDraft(request)
    setBusy(true); setError('')
    try {
      const validation = await api.validatePublication(request)
      setReview({ request, quantity: product.availableQuantity, productName: product.name,
        warnings: validation.warnings,
        channelName: channels.find(c => c.id === Number(channelId))?.name || '', categoryName: options.categoryName,
        listingTypeName: options.listingTypes.find(t => t.id === request.listingTypeId)?.name || request.listingTypeId })
    } catch (cause) { setError(errorMessage(cause)) } finally { setBusy(false) }
  }

  async function publish() {
    if (!review || busy) return
    // Keep the same request after a lost response or reload so retry cannot create another item.
    try { sessionStorage.setItem(PENDING_KEY, JSON.stringify(review)) }
    catch { setError('Não foi possível salvar a tentativa neste navegador. Habilite o armazenamento antes de publicar.'); return }
    setPending(true); setBusy(true); setError('')
    try {
      const result = await api.publishListing(review.request)
      sessionStorage.removeItem(PENDING_KEY)
      onPublished(result.warnings.includes('description_not_saved')
        ? `Anúncio ${result.listing.listingId} criado. A descrição não foi salva; complete-a no Mercado Livre.`
        : result.listing.status === 'ACTIVE' ? `Anúncio ${result.listing.listingId} publicado no Mercado Livre.`
        : `Anúncio ${result.listing.listingId} criado no Mercado Livre, mas ainda não está ativo. Confira as pendências na plataforma.`)
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 400) {
        sessionStorage.removeItem(PENDING_KEY); setPending(false); setReview(null)
        setError(errorMessage(cause))
      } else setError(`Não foi possível confirmar o resultado. Confira seus anúncios no Mercado Livre antes de iniciar outra publicação. Você pode tentar recuperar esta mesma tentativa. ${errorMessage(cause)}`)
    } finally { setBusy(false) }
  }

  function initialValue(field: PublicationField, terms = false) {
    const value = (terms ? draft?.saleTerms : draft?.attributes)?.find(v => v.id === field.id)
    return value?.valueName || field.values.find(v => v.id === value?.valueId)?.name
  }

  return <div className="overlay"><section className="dialog product-edit-dialog publication-dialog" role="dialog" aria-modal="true" aria-labelledby="publication-title">
    <div className="review-top"><div><p className="date">ANÚNCIOS / MERCADO LIVRE</p><h2 id="publication-title">{review ? 'Revisar publicação' : 'Criar anúncio'}</h2></div>
      <button type="button" className="close" aria-label="Fechar" onClick={onClose} disabled={busy}><X size={19}/></button></div>
    {error && <p className="form-error" role="alert">{error}</p>}
    {review ? <><p className="connection-help">Ao confirmar, um anúncio real será criado na conta conectada. As tarifas e regras do Mercado Livre se aplicam.</p>
      {!!review.warnings?.length && <div className="connection-warning" role="status"><b>Avisos do Mercado Livre</b><ul>{review.warnings.map((issue, index) => <li key={index}>{warningMessage(issue)}</li>)}</ul><span>Revise estes avisos antes de confirmar a publicação.</span></div>}
      <dl className="publication-review"><dt>Produto</dt><dd>{review.productName}</dd><dt>Canal</dt><dd>{review.channelName}</dd><dt>Nome</dt><dd>{review.request.name}</dd>
        <dt>Categoria</dt><dd>{review.categoryName}</dd><dt>Preço</dt><dd>{review.request.price.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })}</dd>
        <dt>Estoque</dt><dd>{review.quantity} unidades (será conferido novamente ao publicar)</dd><dt>Tipo</dt><dd>{review.listingTypeName}</dd><dt>Fotos</dt><dd>{review.request.pictures.length}</dd>
        <dt>Envio</dt><dd>{review.request.shippingMode === 'me2' ? 'Mercado Envios' : 'A combinar'} · {review.request.freeShipping ? 'frete grátis' : 'sem frete grátis solicitado'}{review.request.localPickUp ? ' · retirada permitida' : ''}</dd></dl>
      {pending && <p className="connection-help">Esta tentativa foi salva. Repeti-la usa a mesma identificação para evitar duplicação.</p>}
      <div className="review-actions"><button type="button" className="filter-button" disabled={busy || pending} onClick={() => setReview(null)}>Voltar e editar</button>
        <button type="button" className="primary" disabled={busy} onClick={() => void publish()}>{busy ? 'Processando…' : pending ? 'Recuperar tentativa' : 'Confirmar e publicar'}</button></div>
    </> : <form onSubmit={prepare}>
      <p className="connection-help">Publique um produto sem variações. Selecione a categoria para consultar os campos exigidos. As fotos devem estar disponíveis em endereços HTTPS públicos.</p>
      <label className="edit-field">Produto<select required value={productId} disabled={busy} onChange={e => {
        const p = products.find(p => p.id === Number(e.target.value)); setProductId(e.target.value); setName(p?.name || ''); setPrice(String(p?.basePrice || '')); setDescription(p?.description || ''); setQuery(p?.name || ''); setOptions(null); setCategories([]); setCategoryId(''); setDraft(null)
      }}><option value="">Selecione o produto</option>{products.filter(p => p.active && p.availableQuantity > 0).map(p => <option key={p.id} value={p.id}>{p.name} · {p.availableQuantity} unidades</option>)}</select></label>
      <label className="edit-field">Canal<select required value={channelId} disabled={busy} onChange={e => { setChannelId(e.target.value); setOptions(null); setCategories([]); setCategoryId(''); setDraft(null); ++sequence.current }}>
        <option value="">Selecione um canal autorizado</option>{channels.filter(c => c.active && c.type === 'MERCADO_LIVRE').map(c => <option key={c.id} value={c.id}>{c.name}</option>)}</select></label>
      <label className="edit-field">Buscar categoria<input value={query} onChange={e => setQuery(e.target.value)} maxLength={120}/></label>
      <button type="button" className="filter-button" disabled={!channelId || !query.trim() || busy} onClick={() => void search()}>{busy ? 'Consultando…' : 'Buscar categorias'}</button>
      {categories.length > 0 && <label className="edit-field">Categoria<select value={categoryId} disabled={busy} onChange={e => { setCategoryId(e.target.value); void chooseCategory(e.target.value) }}>
        <option value="" disabled>Selecione uma categoria</option>{categories.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}</select></label>}
      {options && <div key={`${channelId}-${options.categoryId}`}>
        <label className="edit-field">{options.userProducts ? 'Nome da família de produtos' : 'Título do anúncio'}<input required value={name} onChange={e => setName(e.target.value)} maxLength={options.maxNameLength}/></label>
        <label className="edit-field">Preço no anúncio (R$)<input type="number" min="0.01" step="0.01" required value={price} onChange={e => setPrice(e.target.value)}/><small className="field-help">O preço inicial não altera o preço-base do produto. Mudanças futuras de preço ainda não são sincronizadas.</small></label>
        <label className="edit-field">Tipo de anúncio<select name="listingTypeId" required defaultValue={draft?.listingTypeId || ''}><option value="" disabled>Selecione</option>{options.listingTypes.map(t => <option key={t.id} value={t.id}>{t.name}</option>)}</select><small className="field-help">O tipo escolhido pode gerar tarifas cobradas pelo Mercado Livre.</small></label>
        <label className="edit-field">Fotos (uma URL HTTPS por linha)<textarea name="pictures" defaultValue={draft?.pictures.join('\n')} required rows={4} placeholder="https://seusite.com/foto-do-produto.jpg"/><small className="field-help">Máximo de {options.maxPictures} fotos.</small></label>
        <h3>Características obrigatórias</h3>{options.attributes.filter(f => f.required).map(f => <Field key={f.id} field={f} prefix="attr_" initial={initialValue(f)}/>)}
        <details><summary>Outras características</summary>{options.attributes.filter(f => !f.required).map(f => <Field key={f.id} field={f} prefix="attr_" initial={initialValue(f)}/>)}</details>
        {options.saleTerms.length > 0 && <><h3>Condições de venda e garantia</h3>{options.saleTerms.filter(f => f.required || f.id.startsWith('WARRANTY_')).map(f => <Field key={f.id} field={f} prefix="term_" initial={initialValue(f, true)}/>)}<details><summary>Outras condições de venda</summary>{options.saleTerms.filter(f => !f.required && !f.id.startsWith('WARRANTY_')).map(f => <Field key={f.id} field={f} prefix="term_" initial={initialValue(f, true)}/>)}</details></>}
        <label className="edit-field">Envio<select name="shippingMode" required defaultValue={draft?.shippingMode || (options.shippingModes.includes('me2') ? 'me2' : '')}><option value="" disabled>Selecione</option>{options.shippingModes.map(m => <option key={m} value={m}>{m === 'me2' ? 'Mercado Envios' : 'A combinar'}</option>)}</select></label>
        <label className="publication-check"><input type="checkbox" name="freeShipping" defaultChecked={draft?.freeShipping}/> Oferecer frete grátis</label>
        {options.localPickUpAllowed && <label className="publication-check"><input type="checkbox" name="localPickUp" defaultChecked={draft?.localPickUp}/> Permitir retirada pessoalmente</label>}
        <label className="edit-field">Descrição<textarea rows={5} maxLength={50000} value={description} onChange={e => setDescription(e.target.value)}/></label>
        {(!options.listingTypes.length || !options.shippingModes.length) && <p className="form-error">Esta conta/categoria não oferece um tipo de anúncio ou envio compatível com este formulário.</p>}
      </div>}
      <div className="review-actions"><button type="button" className="filter-button" disabled={busy} onClick={onClose}>Cancelar</button><button className="primary" disabled={busy || !options || !options.listingTypes.length || !options.shippingModes.length}>{busy ? 'Validando…' : 'Validar e revisar'}</button></div>
    </form>}
  </section></div>
}

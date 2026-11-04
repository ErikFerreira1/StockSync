import type { ChannelType, DemoMarketplaceListing, IntegrationCredential, ListingStatus, LoginResponse, MarketplaceListing, Page, Product, SalesChannel, StockMovement, SyncEvent, PublicationOptions, PublicationRequest } from './types'

const API_URL = import.meta.env.VITE_API_URL ?? '/api'
const TOKEN_KEY = 'stocksync.accessToken'

export class ApiError extends Error {
  issues: string[] = []
  status: number
  fieldErrors: { field: string; message: string }[]
  retryAfter: string | null

  constructor(status: number, message: string, fieldErrors: { field: string; message: string }[] = [], retryAfter: string | null = null) {
    super(message)
    this.status = status
    this.fieldErrors = fieldErrors
    this.retryAfter = retryAfter
  }
}

export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY)
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  if (init.body) headers.set('Content-Type', 'application/json')
  const token = getToken()
  if (token) headers.set('Authorization', `Bearer ${token}`)

  const response = await fetch(`${API_URL}${path}`, { ...init, headers })
  if (response.status === 204) return undefined as T

  const body = await response.json().catch(() => null)
  if (!response.ok) {
    const message = body?.message ?? body?.error ?? `Erro na requisição (${response.status})`
    if (response.status === 401) {
      clearToken()
      window.dispatchEvent(new Event('stocksync:unauthorized'))
    }
    const error = new ApiError(response.status, message, Array.isArray(body?.errors) ? body.errors : [], response.headers.get('Retry-After'))
    error.issues = Array.isArray(body?.issues) ? body.issues : []
    throw error
  }
  return body as T
}

export async function login(username: string, password: string) {
  const result = await request<LoginResponse>('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  })
  localStorage.setItem(TOKEN_KEY, result.accessToken)
  return result
}

export function getLoginError(error: unknown) {
  if (error instanceof ApiError) {
    if (error.status === 401) return 'Usuário ou senha incorretos.'
    if (error.status === 403) return 'A conta não tem permissão para entrar.'
    if (error.status === 429) return `Muitas tentativas. Aguarde ${error.retryAfter ? `${error.retryAfter} segundos` : 'um pouco'} e tente novamente.`
    return 'A API não conseguiu concluir o login. Tente novamente.'
  }
  return 'Não foi possível conectar à API. Confirme que o StockSync está em execução.'
}

export const api = {
  publicationCategories: (salesChannelId: number, query: string) =>
    request<{ id: string; name: string }[]>(`/marketplace-publications/categories?salesChannelId=${salesChannelId}&query=${encodeURIComponent(query)}`),
  publicationOptions: (salesChannelId: number, categoryId: string) =>
    request<PublicationOptions>(`/marketplace-publications/options?salesChannelId=${salesChannelId}&categoryId=${encodeURIComponent(categoryId)}`),
  validatePublication: (requestBody: PublicationRequest) =>
    request<{ warnings: string[] }>('/marketplace-publications/validate', { method: 'POST', body: JSON.stringify(requestBody) }),
  publishListing: (requestBody: PublicationRequest) =>
    request<{ listing: MarketplaceListing; warnings: string[] }>('/marketplace-publications', { method: 'POST', body: JSON.stringify(requestBody) }),
  products: () => request<Page<Product>>('/products?size=100&sort=name,asc'),
  product: (id: number) => request<Product>(`/products/${id}`),
  createProduct: (product: { sku: string; name: string; description: string; basePrice: number; initialQuantity: number; minQuantity: number }) =>
    request<Product>('/products', { method: 'POST', body: JSON.stringify(product) }),
  salesChannels: () => request<Page<SalesChannel>>('/sales-channels?size=100&sort=name,asc'),
  createSalesChannel: (channel: { name: string; type: ChannelType; baseUrl: string }) =>
    request<SalesChannel>('/sales-channels', { method: 'POST', body: JSON.stringify(channel) }),
  updateSalesChannel: (id: number, channel: { name: string; type: ChannelType; baseUrl: string }) =>
    request<SalesChannel>(`/sales-channels/${id}`, { method: 'PUT', body: JSON.stringify(channel) }),
  setSalesChannelActive: (id: number, active: boolean) =>
    request<void>(`/sales-channels/${id}/${active ? 'activate' : 'deactivate'}`, { method: 'PATCH' }),
  integrationCredential: (salesChannelId: number) =>
    request<IntegrationCredential>(`/integration-credentials/sales-channel/${salesChannelId}`),
  saveIntegrationCredential: (salesChannelId: number, credentials: { clientId: string; clientSecret: string }) =>
    request<IntegrationCredential>(`/integration-credentials/sales-channel/${salesChannelId}`, { method: 'PUT', body: JSON.stringify(credentials) }),
  mercadoLivreAuthorizationUrl: (salesChannelId: number) =>
    request<{ authorizationUrl: string }>(`/mercadolivre/authorization-url/${salesChannelId}`),
  listings: () => request<Page<MarketplaceListing>>('/marketplace-listings?size=100&sort=id,desc'),
  createListing: (listing: { productId: number; salesChannelId: number; listingId: string; listingUrl: string; status: ListingStatus }) =>
    request<MarketplaceListing>('/marketplace-listings', { method: 'POST', body: JSON.stringify(listing) }),
  updateProduct: (id: number, product: { sku: string; name: string; description: string; basePrice: number }) =>
    request<Product>(`/products/${id}`, { method: 'PUT', body: JSON.stringify(product) }),
  setProductActive: (id: number, active: boolean) =>
    request<void>(`/products/${id}/${active ? 'activate' : 'deactivate'}`, { method: 'PATCH' }),
  inventory: (id: number) => request<{ availableQuantity: number; minQuantity: number }>(`/inventory/${id}`),
  updateMinQuantity: (id: number, minQuantity: number) =>
    request<{ availableQuantity: number; minQuantity: number }>(`/inventory/product/${id}/min-quantity`, { method: 'PATCH', body: JSON.stringify({ minQuantity }) }),
  updateAvailableQuantity: (id: number, quantity: number) =>
    request<{ availableQuantity: number; minQuantity: number }>(`/inventory/product/${id}/quantity`, { method: 'PATCH', body: JSON.stringify({ productId: id, quantity }) }),
  movements: (id: number) => request<Page<StockMovement>>(`/stock-movement/product/${id}?size=10&sort=occurredAt,desc`),
  failedSyncs: () => request<Page<SyncEvent>>('/sync-events/failed?size=10&sort=timestamp,desc'),
  productSyncs: (id: number) => request<Page<SyncEvent>>(`/sync-events/product/${id}?size=10&sort=timestamp,desc`),
  demoMarketplaceListings: () => request<DemoMarketplaceListing[]>('/demo/marketplace-listings'),
  moveStock: (productId: number, quantity: number, type: 'MANUAL_INCREASE' | 'MANUAL_DECREASE', note: string) =>
    request<StockMovement>('/stock-movement', {
      method: 'POST',
      body: JSON.stringify({ productId, quantity, type, note }),
    }),
}

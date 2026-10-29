import type { LoginResponse, Page, Product, StockMovement, SyncEvent } from './types'

const API_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:8080'
const TOKEN_KEY = 'stocksync.accessToken'

export class ApiError extends Error {
  status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
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
    if (response.status === 401) clearToken()
    throw new ApiError(response.status, message)
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

export const api = {
  products: () => request<Page<Product>>('/products?size=100&sort=name,asc'),
  product: (id: number) => request<Product>(`/products/${id}`),
  inventory: (id: number) => request<{ availableQuantity: number; minQuantity: number }>(`/inventory/${id}`),
  movements: (id: number) => request<Page<StockMovement>>(`/stock-movement/product/${id}?size=10&sort=occurredAt,desc`),
  failedSyncs: () => request<Page<SyncEvent>>('/sync-events/failed?size=10&sort=occurredAt,desc'),
  productSyncs: (id: number) => request<Page<SyncEvent>>(`/sync-events/product/${id}?size=10&sort=occurredAt,desc`),
  moveStock: (productId: number, quantity: number, type: 'MANUAL_INCREASE' | 'MANUAL_DECREASE', note: string) =>
    request<StockMovement>('/stock-movement', {
      method: 'POST',
      body: JSON.stringify({ productId, quantity, type, note }),
    }),
}

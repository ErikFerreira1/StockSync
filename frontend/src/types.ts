export type User = {
  id?: number
  username: string
  role?: string
}

export type LoginResponse = {
  accessToken: string
  tokenType: string
  expiresAt: string
  user: User
}

export type Product = {
  id: number
  sku: string
  name: string
  description: string | null
  basePrice: number
  active: boolean
  availableQuantity: number
}

export type Page<T> = {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export type Inventory = {
  id: number
  productId: number
  availableQuantity: number
  minQuantity: number
  updatedAt: string
}

export type StockMovement = {
  id: number
  productId: number
  occurredAt: string
  quantity: number
  type: 'MANUAL_INCREASE' | 'MANUAL_DECREASE' | string
  originType: string
  originId: number | null
  note: string | null
}

export type SyncEvent = {
  id: number
  productId: number | null
  salesChannelId: number | null
  orderId: number | null
  externalOrderId: string | null
  occurredAt: string
  status: 'SUCCESS' | 'FAILED' | string
  errorMessage: string | null
  attempts: number
}

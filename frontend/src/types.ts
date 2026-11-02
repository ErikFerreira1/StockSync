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

export type ChannelType = 'MERCADO_LIVRE' | 'SHOPEE' | 'OTHER'
export type SalesChannel = {
  id: number
  name: string
  type: ChannelType
  baseUrl: string | null
  active: boolean
}

export type ListingStatus = 'ACTIVE' | 'INACTIVE' | 'PAUSED' | 'CLOSED'
export type DemoMarketplaceListing = {
  listingId: string
  productId: number
  title: string
  price: number
  availableQuantity: number
  status: ListingStatus
  updatedAt: string
}
export type MarketplaceListing = {
  id: number
  productId: number
  salesChannelId: number
  listingId: string
  listingUrl: string | null
  status: ListingStatus
  lastSyncedAt: string | null
}

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
export type IntegrationCredential = {
  id: number
  salesChannelId: number
  clientId: string
  expiresAt: string | null
  connected: boolean
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

export type PublicationField = {
  id: string
  name: string
  required: boolean
  valueType: string
  values: { id: string; name: string }[]
  units: string[]
  hint: string
}
export type PublicationOptions = {
  categoryId: string
  categoryName: string
  userProducts: boolean
  maxNameLength: number
  maxPictures: number
  attributes: PublicationField[]
  saleTerms: PublicationField[]
  listingTypes: { id: string; name: string }[]
  shippingModes: string[]
  localPickUpAllowed: boolean
}
export type PublicationRequest = {
  requestId: string
  productId: number
  salesChannelId: number
  name: string
  categoryId: string
  price: number
  listingTypeId: string
  pictures: string[]
  attributes: { id: string; valueId?: string; valueName?: string }[]
  saleTerms: { id: string; valueId?: string; valueName?: string }[]
  shippingMode: string
  freeShipping: boolean
  localPickUp: boolean
  description: string
}

export type OrderStatus = 'PENDING' | 'PAID' | 'CANCELED' | 'REFUNDED' | 'COMPLETED'
export type OrderItem = {
  id: number
  productId: number
  productName: string
  quantity: number
  unitPrice: number
  subtotal: number
}
export type Order = {
  id: number
  salesChannelId: number
  externalOrderId: string
  orderDate: string
  status: OrderStatus
  items: OrderItem[]
}
export type OrderRequest = {
  externalOrderId: string
  salesChannelId: number
  orderDate: string
  items: { productId: number; quantity: number; unitPrice: number }[]
}

import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import OrdersPage from '../src/OrdersPage'
import { api, ApiError } from '../src/api'
import type { Order, Page, Product, SalesChannel } from '../src/types'

vi.mock('../src/api', async () => {
  const actual = await vi.importActual<typeof import('../src/api')>('../src/api')
  return { ...actual, api: { ...actual.api, orders: vi.fn(), order: vi.fn(), createOrder: vi.fn(), cancelOrder: vi.fn(), synchronizeOrders: vi.fn(), failedSyncs: vi.fn() } }
})

const products: Product[] = [
  { id: 1, sku: 'USB', name: 'Cabo USB-C', basePrice: 10, availableQuantity: 4, active: true, description: null },
  { id: 2, sku: 'ADP', name: 'Adaptador', basePrice: 1.25, availableQuantity: 8, active: true, description: null },
]
const channels: SalesChannel[] = [
  { id: 1, name: 'Mercado Livre', type: 'MERCADO_LIVRE', active: true, baseUrl: null },
  { id: 2, name: 'Balcão', type: 'OTHER', active: true, baseUrl: null },
  { id: 3, name: 'Canal inativo', type: 'MERCADO_LIVRE', active: false, baseUrl: null },
]
const order: Order = { id: 21, salesChannelId: 1, externalOrderId: 'ML-21', status: 'PENDING', orderDate: '2026-11-04T12:00:00Z',
  items: [{ id: 5, productId: 1, productName: 'Cabo USB-C', quantity: 2, unitPrice: 10, subtotal: 20 }] }
const page = (content: Order[], current = 0, pages = 1): Page<Order> => ({ content, number: current, size: 10, totalPages: pages, totalElements: pages === 1 ? content.length : 21 })

function props(overrides: Partial<Parameters<typeof OrdersPage>[0]> = {}) {
  return { products, channels, role: 'OPERATOR', onSelect: vi.fn(), onStockChanged: vi.fn(), demoMode: false, ...overrides }
}

beforeEach(() => {
  vi.mocked(api.orders).mockReset().mockResolvedValue(page([order]))
  vi.mocked(api.order).mockReset().mockResolvedValue(order)
  vi.mocked(api.createOrder).mockReset().mockResolvedValue(order)
  vi.mocked(api.cancelOrder).mockReset().mockResolvedValue({ ...order, status: 'CANCELED' })
  vi.mocked(api.synchronizeOrders).mockReset().mockResolvedValue(undefined)
  vi.mocked(api.failedSyncs).mockReset().mockResolvedValue({ content: [], number: 0, size: 10, totalPages: 0, totalElements: 0 })
})

async function fillOrder() {
  const user = userEvent.setup()
  await user.click(await screen.findByRole('button', { name: 'Novo pedido' }))
  await user.type(screen.getByLabelText('Identificador do pedido'), 'MANUAL-21')
  await user.selectOptions(screen.getByLabelText('Canal de venda'), '2')
  fireEvent.change(screen.getByLabelText('Data do pedido'), { target: { value: '2026-11-04T09:00' } })
  await user.selectOptions(screen.getByLabelText('Produto do item 1'), '1')
  await user.clear(screen.getByLabelText('Quantidade do item 1'))
  await user.type(screen.getByLabelText('Quantidade do item 1'), '2')
  return user
}

describe('Orders list and navigation', () => {
  it('shows a loading state and then the server order data and totals', async () => {
    render(<OrdersPage {...props()}/>)
    expect(screen.getByText('Carregando pedidos…')).toBeTruthy()
    expect(await screen.findByText('ML-21')).toBeTruthy()
    expect(screen.getByText('2 un.')).toBeTruthy()
    expect(screen.getByText(/20,00/)).toBeTruthy()
    expect(screen.getByText('Pendente')).toBeTruthy()
  })

  it('uses server pagination and forwards a detail selection to the route', async () => {
    const p = props()
    vi.mocked(api.orders).mockImplementation(async current => page([{ ...order, externalOrderId: current ? 'ML-22' : 'ML-21' }], current, 3))
    render(<OrdersPage {...p}/>)
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Detalhes do pedido ML-21' }))
    expect(p.onSelect).toHaveBeenCalledWith(21)
    await user.click(screen.getByRole('button', { name: 'Próxima' }))
    expect(await screen.findByText('ML-22')).toBeTruthy()
    expect(api.orders).toHaveBeenLastCalledWith(1)
    expect(screen.getByText(/Página 2 de 3/)).toBeTruthy()
  })

  it('shows an empty state', async () => {
    vi.mocked(api.orders).mockResolvedValue(page([]))
    render(<OrdersPage {...props()}/>)
    expect(await screen.findByText('Nenhum pedido registrado')).toBeTruthy()
  })

  it('can retry a list error', async () => {
    vi.mocked(api.orders).mockRejectedValueOnce(new ApiError(500, 'Falha temporária'))
    render(<OrdersPage {...props()}/>)
    expect(await screen.findByRole('alert')).toBeTruthy()
    await userEvent.click(screen.getByRole('button', { name: 'Tentar novamente' }))
    expect(await screen.findByText('ML-21')).toBeTruthy()
  })

  it('ignores an older response after a refresh', async () => {
    let resolveOld!: (result: Page<Order>) => void
    vi.mocked(api.orders).mockReturnValueOnce(new Promise(resolve => { resolveOld = resolve }))
    const p = props()
    const view = render(<OrdersPage {...p}/>)
    view.rerender(<OrdersPage {...p} products={[...products]}/>)
    expect(await screen.findByText('ML-21')).toBeTruthy()
    await act(async () => { resolveOld(page([{ ...order, externalOrderId: 'STALE' }])) })
    expect(screen.queryByText('STALE')).toBeNull()
  })

  it.each(['VIEWER', undefined])('does not offer write operations to role %s, including on a deep-linked detail', async role => {
    render(<OrdersPage {...props({ role, selectedId: 21 })}/>)
    expect(await screen.findByRole('heading', { name: 'ML-21' })).toBeTruthy()
    expect(screen.queryByRole('button', { name: 'Novo pedido' })).toBeNull()
    expect(screen.queryByRole('button', { name: 'Sincronizar pedidos' })).toBeNull()
    expect(screen.queryByRole('button', { name: 'Cancelar no StockSync' })).toBeNull()
  })
})

describe('Manual order creation', () => {
  it('reviews multiple items and only sends the request after explicit confirmation', async () => {
    const p = props()
    render(<OrdersPage {...p}/>)
    const user = await fillOrder()
    await user.click(screen.getByRole('button', { name: 'Adicionar item' }))
    await user.selectOptions(screen.getByLabelText('Produto do item 2'), '2')
    await user.clear(screen.getByLabelText('Quantidade do item 2'))
    await user.type(screen.getByLabelText('Quantidade do item 2'), '3')
    await user.click(screen.getByRole('button', { name: 'Revisar pedido' }))
    expect(screen.getByRole('heading', { name: 'Revisar pedido' })).toBeTruthy()
    expect(screen.getByText(/23,75/)).toBeTruthy()
    expect(api.createOrder).not.toHaveBeenCalled()
    await user.click(screen.getByRole('button', { name: 'Confirmar pedido e baixar estoque' }))
    await waitFor(() => expect(api.createOrder).toHaveBeenCalledWith({ externalOrderId: 'MANUAL-21', salesChannelId: 2,
      orderDate: new Date('2026-11-04T09:00').toISOString(), items: [{ productId: 1, quantity: 2, unitPrice: 10 }, { productId: 2, quantity: 3, unitPrice: 1.25 }] }))
    expect(p.onStockChanged).toHaveBeenCalledOnce()
    expect(p.onSelect).toHaveBeenCalledWith(21)
  })

  it('prevents quantities above known stock and prevents duplicate product selections', async () => {
    render(<OrdersPage {...props()}/>)
    const user = await fillOrder()
    await user.click(screen.getByRole('button', { name: 'Adicionar item' }))
    const second = screen.getByLabelText('Produto do item 2')
    expect(within(second).getByRole('option', { name: /Cabo USB-C/ }).hasAttribute('disabled')).toBe(true)
    await user.click(screen.getByRole('button', { name: 'Remover item 2' }))
    await user.clear(screen.getByLabelText('Quantidade do item 1'))
    await user.type(screen.getByLabelText('Quantidade do item 1'), '5')
    await user.click(screen.getByRole('button', { name: 'Revisar pedido' }))
    expect(screen.queryByRole('heading', { name: 'Revisar pedido' })).toBeNull()
    expect(api.createOrder).not.toHaveBeenCalled()
  })

  it('keeps entered data when the server detects insufficient stock', async () => {
    vi.mocked(api.createOrder).mockRejectedValue(new ApiError(409, 'Insufficient stock. Available: 1'))
    render(<OrdersPage {...props()}/>)
    const user = await fillOrder()
    await user.click(screen.getByRole('button', { name: 'Revisar pedido' }))
    await user.click(screen.getByRole('button', { name: 'Confirmar pedido e baixar estoque' }))
    expect((await screen.findByRole('alert')).textContent).toContain('Estoque insuficiente')
    expect((screen.getByLabelText('Identificador do pedido') as HTMLInputElement).value).toBe('MANUAL-21')
    expect((screen.getByLabelText('Quantidade do item 1') as HTMLInputElement).value).toBe('2')
    expect(api.createOrder).toHaveBeenCalledOnce()
  })

  it('disables confirmation and closing while a creation response is pending', async () => {
    let finish!: (result: Order) => void
    vi.mocked(api.createOrder).mockReturnValue(new Promise(resolve => { finish = resolve }))
    render(<OrdersPage {...props()}/>)
    const user = await fillOrder()
    await user.click(screen.getByRole('button', { name: 'Revisar pedido' }))
    await user.click(screen.getByRole('button', { name: 'Confirmar pedido e baixar estoque' }))
    expect(screen.getByRole('button', { name: 'Cadastrando…' }).hasAttribute('disabled')).toBe(true)
    expect(screen.getByRole('button', { name: 'Fechar cadastro' }).hasAttribute('disabled')).toBe(true)
    expect(api.createOrder).toHaveBeenCalledOnce()
    await act(async () => { finish(order) })
  })
})

describe('Order cancellation and import', () => {
  it('requires confirmation and explains that cancellation affects only StockSync', async () => {
    const p = props({ selectedId: 21 })
    render(<OrdersPage {...p}/>)
    await userEvent.click(await screen.findByRole('button', { name: 'Cancelar no StockSync' }))
    expect(screen.getByText(/não cancela a venda no Mercado Livre nem gera reembolso/)).toBeTruthy()
    expect(api.cancelOrder).not.toHaveBeenCalled()
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar cancelamento' }))
    await waitFor(() => expect(api.cancelOrder).toHaveBeenCalledWith(21))
    expect(await screen.findByText('Cancelado')).toBeTruthy()
    expect(p.onStockChanged).toHaveBeenCalledOnce()
  })

  it.each(['CANCELED', 'COMPLETED', 'REFUNDED'] as const)('does not offer cancellation for a %s order', async status => {
    vi.mocked(api.order).mockResolvedValue({ ...order, status })
    render(<OrdersPage {...props({ selectedId: 21 })}/>)
    await screen.findByRole('heading', { name: 'ML-21' })
    expect(screen.queryByRole('button', { name: 'Cancelar no StockSync' })).toBeNull()
  })

  it('shows an error when opening a missing deep-linked order', async () => {
    vi.mocked(api.order).mockRejectedValue(new ApiError(404, 'Order not found'))
    render(<OrdersPage {...props({ selectedId: 999 })}/>)
    expect((await screen.findByRole('alert')).textContent).toContain('Order not found')
  })

  it('imports only after channel selection and exposes possible per-order failures', async () => {
    const p = props()
    vi.mocked(api.failedSyncs).mockResolvedValue({ content: [{ id: 1, productId: null, salesChannelId: 1, orderId: null, externalOrderId: 'ML-FAILED', occurredAt: order.orderDate, status: 'FAILURE', errorMessage: 'Insufficient stock', attempts: 1 }], totalElements: 1, totalPages: 1, number: 0, size: 10 })
    render(<OrdersPage {...p}/>)
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Sincronizar pedidos' }))
    expect(screen.queryByRole('option', { name: 'Canal inativo' })).toBeNull()
    expect(screen.queryByRole('option', { name: 'Balcão' })).toBeNull()
    expect(api.synchronizeOrders).not.toHaveBeenCalled()
    await user.selectOptions(screen.getByLabelText('Canal de venda'), '1')
    await user.click(screen.getByRole('button', { name: 'Buscar pedidos no canal' }))
    expect(await screen.findByText('ML-FAILED')).toBeTruthy()
    expect(screen.getByText(/Podem incluir tentativas anteriores/)).toBeTruthy()
    expect(api.synchronizeOrders).toHaveBeenCalledWith(1)
    expect(p.onStockChanged).toHaveBeenCalledOnce()
  })

  it('reports a failed import without claiming success', async () => {
    vi.mocked(api.synchronizeOrders).mockRejectedValue(new ApiError(502, 'Marketplace unavailable'))
    render(<OrdersPage {...props()}/>)
    await userEvent.click(await screen.findByRole('button', { name: 'Sincronizar pedidos' }))
    await userEvent.selectOptions(screen.getByLabelText('Canal de venda'), '1')
    await userEvent.click(screen.getByRole('button', { name: 'Buscar pedidos no canal' }))
    expect((await screen.findByRole('alert')).textContent).toContain('Marketplace unavailable')
    expect(screen.queryByText(/Consulta concluída/)).toBeNull()
    expect(api.failedSyncs).not.toHaveBeenCalled()
  })

  it('distinguishes a finished import from a failure to load its audit history', async () => {
    vi.mocked(api.failedSyncs).mockRejectedValue(new ApiError(500, 'Audit unavailable'))
    render(<OrdersPage {...props()}/>)
    await userEvent.click(await screen.findByRole('button', { name: 'Sincronizar pedidos' }))
    await userEvent.selectOptions(screen.getByLabelText('Canal de venda'), '1')
    await userEvent.click(screen.getByRole('button', { name: 'Buscar pedidos no canal' }))
    expect((await screen.findByRole('alert')).textContent).toContain('A consulta terminou')
    expect(screen.getByText(/Consulta concluída/)).toBeTruthy()
    expect(screen.queryByText(/Nenhuma falha de pedido encontrada/)).toBeNull()
  })
})

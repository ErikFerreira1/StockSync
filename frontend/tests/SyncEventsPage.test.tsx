import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, it, vi } from 'vitest'
import SyncEventsPage from '../src/SyncEventsPage'
import { api, ApiError } from '../src/api'
import type { Page, SyncEvent } from '../src/types'

vi.mock('../src/api', async () => {
  const actual = await vi.importActual<typeof import('../src/api')>('../src/api')
  return { ...actual, api: { ...actual.api, syncEvents: vi.fn() } }
})

const event: SyncEvent = { id: 1, productId: 10, salesChannelId: 1, orderId: null, externalOrderId: null,
  occurredAt: '2026-11-06T12:00:00Z', status: 'FAILURE', errorMessage: 'Insufficient stock', attempts: 1 }
const products = [{ id: 10, name: 'Cabo USB-C', sku: 'USB', description: null, basePrice: 10, active: true, availableQuantity: 12 }]
const channels = [{ id: 1, name: 'Mercado Livre', type: 'MERCADO_LIVRE' as const, baseUrl: null, active: true }]
const page = (content: SyncEvent[] = [event], pages = 1): Page<SyncEvent> => ({ content, totalElements: pages === 1 ? content.length : 21, totalPages: pages, number: 0, size: 20 })
function props() { return { products, channels, onProduct: vi.fn(), onOrder: vi.fn() } }
beforeEach(() => vi.mocked(api.syncEvents).mockReset().mockResolvedValue(page()))

it('starts with failures, shows translated details and links to the product', async () => {
  const p = props()
  render(<SyncEventsPage {...p}/>)
  expect(await screen.findByText('O estoque era insuficiente para concluir a operação.')).toBeTruthy()
  expect(api.syncEvents).toHaveBeenCalledWith(expect.objectContaining({ status: 'FAILURE' }), 0)
  await userEvent.click(screen.getByRole('button', { name: 'Abrir produto' }))
  expect(p.onProduct).toHaveBeenCalledWith(10)
})

it('applies all filters together and does not request on each edit', async () => {
  render(<SyncEventsPage {...props()}/>)
  await screen.findByText('Cabo USB-C', { selector: 'b' })
  const user = userEvent.setup()
  await user.selectOptions(screen.getByLabelText('Resultado'), 'SUCCESS')
  await user.selectOptions(screen.getByLabelText('Origem'), 'ORDER')
  await user.selectOptions(screen.getByLabelText('Canal'), '1')
  await user.type(screen.getByLabelText('ID do pedido local'), '21')
  await user.type(screen.getByLabelText('Identificador externo do pedido'), 'EXT-21')
  expect(api.syncEvents).toHaveBeenCalledTimes(1)
  await user.click(screen.getByRole('button', { name: 'Aplicar filtros' }))
  await waitFor(() => expect(api.syncEvents).toHaveBeenLastCalledWith(expect.objectContaining({ status: 'SUCCESS', scope: 'ORDER', salesChannelId: '1', orderId: '21', externalOrderId: 'EXT-21' }), 0))
})

it('preserves filters on pagination and resets the page when clearing them', async () => {
  vi.mocked(api.syncEvents).mockResolvedValue(page([event], 2))
  render(<SyncEventsPage {...props()}/>)
  await userEvent.click(await screen.findByRole('button', { name: 'Próxima' }))
  await waitFor(() => expect(api.syncEvents).toHaveBeenLastCalledWith(expect.objectContaining({ status: 'FAILURE' }), 1))
  await userEvent.click(screen.getByRole('button', { name: 'Limpar filtros' }))
  await waitFor(() => expect(api.syncEvents).toHaveBeenLastCalledWith(expect.objectContaining({ status: '', scope: '', productId: '', salesChannelId: '' }), 0))
})

it('shows order failures without inventing a local order link, and links imported orders', async () => {
  const p = props()
  vi.mocked(api.syncEvents).mockResolvedValue(page([
    { ...event, productId: null, externalOrderId: 'NOT-IMPORTED' },
    { ...event, id: 2, productId: null, externalOrderId: 'IMPORTED', orderId: 21, status: 'SUCCESS', errorMessage: null },
  ]))
  render(<SyncEventsPage {...p}/>)
  expect(await screen.findByText('NOT-IMPORTED')).toBeTruthy()
  expect(screen.getAllByRole('button', { name: /Abrir pedido/ })).toHaveLength(1)
  await userEvent.click(screen.getByRole('button', { name: 'Abrir pedido #21' }))
  expect(p.onOrder).toHaveBeenCalledWith(21)
  expect(screen.getByText('Operação concluída.')).toBeTruthy()
})

it('has explicit empty and retry states', async () => {
  vi.mocked(api.syncEvents).mockRejectedValueOnce(new ApiError(500, 'Falha temporária')).mockResolvedValue(page([]))
  render(<SyncEventsPage {...props()}/>)
  expect(await screen.findByRole('alert')).toBeTruthy()
  await userEvent.click(screen.getByRole('button', { name: 'Tentar novamente' }))
  expect(await screen.findByText('Nenhum evento encontrado')).toBeTruthy()
})

it('does not overwrite newer results with an outdated response', async () => {
  let finish!: (value: Page<SyncEvent>) => void
  vi.mocked(api.syncEvents).mockReturnValueOnce(new Promise(resolve => { finish = resolve }))
  const p = props()
  const view = render(<SyncEventsPage {...p}/>)
  view.rerender(<SyncEventsPage {...p} products={[...products]}/>)
  await screen.findByText('O estoque era insuficiente para concluir a operação.')
  await act(async () => { finish(page([{ ...event, id: 999, externalOrderId: 'STALE', productId: null }])) })
  expect(screen.queryByText('STALE')).toBeNull()
})

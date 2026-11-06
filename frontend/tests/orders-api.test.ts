import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { api, getSessionUser } from '../src/api'

const fetchMock = vi.fn()
beforeEach(() => {
  vi.stubGlobal('fetch', fetchMock)
  fetchMock.mockReset().mockImplementation(async () => ({ ok: true, status: 200, headers: new Headers(), json: async () => ({ content: [] }) }))
})
afterEach(() => vi.unstubAllGlobals())

describe('Orders HTTP contracts', () => {
  it('encodes sync event filters safely and keeps stable pagination sorting', async () => {
    await api.syncEvents({ status: 'FAILURE', productId: '', salesChannelId: '2', externalOrderId: ' EXT&21 ', scope: 'ORDER' }, 1)
    const url = new URL(fetchMock.mock.calls[0][0], 'https://example.com')
    expect(url.pathname).toBe('/api/sync-events')
    expect(url.searchParams.get('externalOrderId')).toBe('EXT&21')
    expect(url.searchParams.get('page')).toBe('1')
    expect(url.searchParams.get('size')).toBe('20')
    expect(url.searchParams.has('productId')).toBe(false)
    expect(url.searchParams.getAll('sort')).toEqual(['timestamp,desc', 'id,desc'])
  })
  it('sends the page and supported sort field to the list endpoint', async () => {
    await api.orders(2)
    expect(fetchMock.mock.calls[0][0]).toBe('/api/orders?page=2&size=10&sort=id,desc')
    await api.order(21)
    expect(fetchMock.mock.calls[1][0]).toBe('/api/orders/21')
  })

  it('preserves the item prices and ISO date in creation and sends the bearer token', async () => {
    localStorage.setItem('stocksync.accessToken', 'test-token')
    const request = { externalOrderId: 'SALE-1', salesChannelId: 2, orderDate: '2026-11-04T12:00:00.000Z',
      items: [{ productId: 1, quantity: 3, unitPrice: 1.25 }] }
    await api.createOrder(request)
    const [path, init] = fetchMock.mock.calls[0]
    expect(path).toBe('/api/orders')
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body)).toEqual(request)
    expect(init.headers.get('Authorization')).toBe('Bearer test-token')
  })

  it('uses PATCH for local cancellation and accepts an empty 204 on import', async () => {
    await api.cancelOrder(21)
    expect(fetchMock.mock.calls[0][0]).toBe('/api/orders/21/cancel')
    expect(fetchMock.mock.calls[0][1].method).toBe('PATCH')
    fetchMock.mockResolvedValueOnce({ ok: true, status: 204, headers: new Headers() })
    await expect(api.synchronizeOrders(2)).resolves.toBeUndefined()
    expect(fetchMock.mock.calls[1][0]).toBe('/api/sales-channels/2/orders/synchronize')
    expect(fetchMock.mock.calls[1][1].method).toBe('POST')
  })
})

describe('Restoring display permissions from an existing session', () => {
  function token(claims: unknown) {
    const bytes = new TextEncoder().encode(JSON.stringify(claims))
    const payload = btoa(Array.from(bytes, byte => String.fromCharCode(byte)).join('')).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_')
    localStorage.setItem('stocksync.accessToken', `header.${payload}.signature`)
  }

  it.each(['ADMIN', 'OPERATOR', 'VIEWER'])('restores the %s role after reloading, including UTF-8 usernames', role => {
    token({ sub: 'João', roles: [`ROLE_${role}`] })
    expect(getSessionUser()).toEqual({ username: 'João', role })
  })

  it('does not grant a write role for malformed or unknown claims', () => {
    expect(getSessionUser()).toBeNull()
    localStorage.setItem('stocksync.accessToken', 'invalid')
    expect(getSessionUser()).toBeNull()
    token({ sub: 'someone', roles: ['ROLE_SUPERUSER'] })
    expect(getSessionUser()?.role).toBeUndefined()
    token({ sub: 123, roles: ['ROLE_ADMIN'] })
    expect(getSessionUser()).toBeNull()
  })
})

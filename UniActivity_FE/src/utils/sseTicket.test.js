import test from 'node:test'
import assert from 'node:assert/strict'

import { requestSseTicket, sseSubscribeUrl } from './sseTicket.js'

test('requestSseTicket uses authenticated POST endpoint', async () => {
    let capturedUrl
    let capturedOptions
    const fetchImpl = async (url, options) => {
        capturedUrl = url
        capturedOptions = options
        return {
            ok: true,
            async json() {
                return { ticket: 'short-ticket', expiresIn: 60 }
            },
        }
    }

    const ticket = await requestSseTicket(fetchImpl)

    assert.equal(capturedUrl, '/sse/ticket')
    assert.equal(capturedOptions.method, 'POST')
    assert.equal(ticket, 'short-ticket')
})

test('sseSubscribeUrl contains only encoded purpose ticket', () => {
    const url = sseSubscribeUrl('ticket+/= value')

    assert.equal(url, '/sse/subscribe?ticket=ticket%2B%2F%3D%20value')
    assert.equal(url.includes('token='), false)
})

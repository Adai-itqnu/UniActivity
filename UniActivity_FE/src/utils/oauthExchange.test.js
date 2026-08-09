import test from 'node:test'
import assert from 'node:assert/strict'

import { exchangeOAuthCode, exchangeOAuthCodeOnce, homePathForRole } from './oauthExchange.js'

test('exchangeOAuthCode posts one-time code and returns JWT response', async () => {
    let capturedUrl
    let capturedOptions
    const fetchImpl = async (url, options) => {
        capturedUrl = url
        capturedOptions = options
        return {
            ok: true,
            async json() {
                return {
                    accessToken: 'access',
                    refreshToken: 'refresh',
                    user: { role: 'STUDENT' },
                }
            },
        }
    }

    const result = await exchangeOAuthCode('one-time-code', fetchImpl)

    assert.equal(capturedUrl, '/api/auth/oauth2/exchange')
    assert.equal(capturedOptions.method, 'POST')
    assert.deepEqual(JSON.parse(capturedOptions.body), { code: 'one-time-code' })
    assert.equal(result.accessToken, 'access')
})

test('exchangeOAuthCode rejects invalid or consumed code', async () => {
    const fetchImpl = async () => ({
        ok: false,
        async json() {
            return { error: 'OAuth exchange code is invalid or expired.' }
        },
    })

    await assert.rejects(
        exchangeOAuthCode('consumed-code', fetchImpl),
        /invalid or expired/
    )
})

test('homePathForRole maps supported roles safely', () => {
    assert.equal(homePathForRole('ADMIN'), '/admin/dashboard')
    assert.equal(homePathForRole('MANAGER'), '/manager/dashboard')
    assert.equal(homePathForRole('STUDENT'), '/student/home')
    assert.equal(homePathForRole('UNKNOWN'), '/')
})

test('exchangeOAuthCodeOnce deduplicates StrictMode callback effects', async () => {
    let requestCount = 0
    const fetchImpl = async () => {
        requestCount += 1
        return {
            ok: true,
            async json() {
                return {
                    accessToken: 'access',
                    refreshToken: 'refresh',
                    user: { role: 'STUDENT' },
                }
            },
        }
    }

    const [first, second] = await Promise.all([
        exchangeOAuthCodeOnce('strict-mode-code', fetchImpl),
        exchangeOAuthCodeOnce('strict-mode-code', fetchImpl),
    ])

    assert.equal(requestCount, 1)
    assert.deepEqual(first, second)
})

test('exchangeOAuthCodeOnce releases a failed request so it can be retried', async () => {
    let requestCount = 0
    const fetchImpl = async () => {
        requestCount += 1
        return {
            ok: false,
            async json() {
                return { error: 'temporary failure' }
            },
        }
    }

    await assert.rejects(exchangeOAuthCodeOnce('retryable-code', fetchImpl), /temporary failure/)
    await assert.rejects(exchangeOAuthCodeOnce('retryable-code', fetchImpl), /temporary failure/)

    assert.equal(requestCount, 2)
})

import test from 'node:test'
import assert from 'node:assert/strict'

import { normalizeToast } from './toast.js'

test('normalizeToast keeps untrusted HTML as text and rejects unknown types', () => {
    const payload = '<img src=x onerror=alert(1)>'

    const normalized = normalizeToast({
        title: payload,
        message: payload,
        type: 'javascript',
    })

    assert.deepEqual(normalized, {
        title: payload,
        message: payload,
        type: 'info',
    })
    assert.equal(Object.hasOwn(normalized, 'html'), false)
})

test('normalizeToast converts nullish and non-string values safely', () => {
    assert.deepEqual(normalizeToast({ title: null, message: 42, type: 'success' }), {
        title: '',
        message: '42',
        type: 'success',
    })
})

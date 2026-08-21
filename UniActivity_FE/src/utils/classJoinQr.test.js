import test from 'node:test'
import assert from 'node:assert/strict'

test('createClassJoinQrDataUrl creates a downloadable PNG data URL', async () => {
    const module = await import('./classJoinQr.js').catch(() => ({}))

    assert.equal(typeof module.createClassJoinQrDataUrl, 'function')

    const dataUrl = await module.createClassJoinQrDataUrl('JOIN1234')

    assert.match(dataUrl, /^data:image\/png;base64,/)
})

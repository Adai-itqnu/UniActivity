import test from 'node:test'
import assert from 'node:assert/strict'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { createServer } from 'vite'

test('class QR modal does not render the protected backend image URL directly', async () => {
    const vite = await createServer({
        appType: 'custom',
        logLevel: 'silent',
        server: { middlewareMode: true },
    })

    try {
        const module = await vite.ssrLoadModule('/src/pages/admin/ClassList.jsx')

        assert.equal(typeof module.ClassQrModal, 'function')

        const markup = renderToStaticMarkup(createElement(module.ClassQrModal, {
            target: { id: 7, code: 'CNTT-K46', name: 'CNTT K46', joinCode: 'JOIN1234' },
            onClose() {},
        }))

        assert.match(markup, /Đang tạo mã QR/)
        assert.doesNotMatch(markup, /\/admin\/classes\/api\/7\/qrcode/)
    } finally {
        await vite.close()
    }
})

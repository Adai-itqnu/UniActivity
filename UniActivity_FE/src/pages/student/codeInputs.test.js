import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

function inputContaining(source, marker) {
    const markerIndex = source.indexOf(marker)
    assert.notEqual(markerIndex, -1, `Expected input marker ${marker}`)
    const start = source.lastIndexOf('<input', markerIndex)
    const end = source.indexOf('/>', markerIndex)
    assert.notEqual(start, -1, `Expected an input before ${marker}`)
    assert.notEqual(end, -1, `Expected an input terminator after ${marker}`)
    return source.slice(start, end + 2)
}

test('student class-code input exposes accessible code-entry attributes', async () => {
    const source = await readFile(new URL('./Dashboard.jsx', import.meta.url), 'utf8')
    const input = inputContaining(source, 'Nhập mã lớp (VD: A7K9P2)')

    assert.match(input, /\bid="class-join-code"/)
    assert.match(input, /\bname="joinCode"/)
    assert.match(input, /\baria-label="Mã tham gia lớp"/)
    assert.match(input, /\bspellCheck=\{false\}/)
    assert.match(input, /\bautoComplete="off"/)
})

test('manual check-in label is associated with a named non-spellchecked input', async () => {
    const source = await readFile(new URL('./Checkin.jsx', import.meta.url), 'utf8')
    const input = inputContaining(source, 'placeholder="A7K9P2"')

    assert.match(source, /<label[^>]*htmlFor="manual-checkin-code"[^>]*>/)
    assert.match(input, /\bid="manual-checkin-code"/)
    assert.match(input, /\bname="checkinCode"/)
    assert.match(input, /\bspellCheck=\{false\}/)
    assert.match(input, /\bautoComplete="off"/)
})

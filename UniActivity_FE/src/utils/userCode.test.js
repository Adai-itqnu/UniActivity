import test from 'node:test'
import assert from 'node:assert/strict'
import { isCompleteUserCode, normalizeUserCode } from './userCode.js'

test('normalizes class and check-in codes to six approved characters', () => {
    assert.equal(normalizeUserCode(' a7-o1k9p2 '), 'A7K9P2')
    assert.equal(normalizeUserCode('A7K9P2ZZ'), 'A7K9P2')
    assert.equal(isCompleteUserCode('A7K9P2'), true)
    assert.equal(isCompleteUserCode('ABCDEF'), false)
    assert.equal(isCompleteUserCode('234567'), false)
})

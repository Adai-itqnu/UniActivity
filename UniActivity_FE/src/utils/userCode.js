const LETTERS = 'ABCDEFGHJKMNPQRSTUVWXYZ'
const DIGITS = '23456789'
const ALLOWED = new Set((LETTERS + DIGITS).split(''))

export function normalizeUserCode(value) {
    return String(value ?? '')
        .trim()
        .toUpperCase()
        .split('')
        .filter(character => ALLOWED.has(character))
        .join('')
        .slice(0, 6)
}

export function isCompleteUserCode(value) {
    const normalized = normalizeUserCode(value)
    return normalized.length === 6
        && [...normalized].some(character => LETTERS.includes(character))
        && [...normalized].some(character => DIGITS.includes(character))
}

const inFlightExchanges = new Map()

export async function exchangeOAuthCode(code, fetchImpl = fetch) {
    const response = await fetchImpl('/api/auth/oauth2/exchange', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ code }),
    })

    const data = await response.json()
    if (!response.ok) {
        throw new Error(data.error || 'Đăng nhập bằng Google thất bại.')
    }
    if (!data.accessToken || !data.refreshToken || !data.user) {
        throw new Error('Phản hồi đăng nhập Google không hợp lệ.')
    }
    return data
}

export function exchangeOAuthCodeOnce(code, fetchImpl = fetch) {
    if (!inFlightExchanges.has(code)) {
        const exchangePromise = exchangeOAuthCode(code, fetchImpl)
        inFlightExchanges.set(code, exchangePromise)
        exchangePromise.then(
            () => {
                if (inFlightExchanges.get(code) === exchangePromise) {
                    inFlightExchanges.delete(code)
                }
            },
            () => {
                if (inFlightExchanges.get(code) === exchangePromise) {
                    inFlightExchanges.delete(code)
                }
            }
        )
    }
    return inFlightExchanges.get(code)
}

export function homePathForRole(role) {
    if (role === 'ADMIN') return '/admin/dashboard'
    if (role === 'MANAGER') return '/manager/dashboard'
    if (role === 'STUDENT') return '/student/home'
    return '/'
}

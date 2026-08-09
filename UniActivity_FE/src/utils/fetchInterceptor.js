const originalFetch = window.fetch

// Các URL KHÔNG cần gắn token (public endpoints)
const PUBLIC_PATHS = [
    '/api/auth/login',
    '/api/auth/register',
    '/api/auth/refresh',
    '/api/auth/oauth2/exchange',
]

function isPublicPath(url) {
    // Chỉ check relative URLs hoặc same-origin
    try {
        const pathname = url.startsWith('http') ? new URL(url).pathname : url.split('?')[0]
        return PUBLIC_PATHS.some(p => pathname === p || pathname.startsWith(p))
    } catch {
        return false
    }
}

// Cờ tránh refresh token đồng thời
let isRefreshing = false
let refreshPromise = null

async function refreshAccessToken() {
    const refreshToken = sessionStorage.getItem('refreshToken')
    if (!refreshToken) throw new Error('No refresh token')

    const response = await originalFetch('/api/auth/refresh', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
    })

    if (!response.ok) throw new Error('Refresh failed')

    const data = await response.json()
    sessionStorage.setItem('accessToken', data.accessToken)
    return data.accessToken
}

window.fetch = async function (url, options = {}) {
    // Không can thiệp vào external URLs (CDN, Google, etc.)
    if (typeof url === 'string' && url.startsWith('http') && !url.startsWith(window.location.origin)) {
        return originalFetch(url, options)
    }

    // Không gắn token cho public paths
    if (typeof url === 'string' && isPublicPath(url)) {
        return originalFetch(url, options)
    }

    const accessToken = sessionStorage.getItem('accessToken')

    // Merge headers — thêm Authorization nếu có token
    const headers = new Headers(options.headers || {})
    if (accessToken && !headers.has('Authorization')) {
        headers.set('Authorization', `Bearer ${accessToken}`)
    }

    let response = await originalFetch(url, {
        ...options,
        headers,
        credentials: 'include',
    })

    // Nếu 401 và có token → thử refresh
    if (response.status === 401 && accessToken) {
        try {
            // Tránh gọi refresh đồng thời từ nhiều request
            if (!isRefreshing) {
                isRefreshing = true
                refreshPromise = refreshAccessToken()
            }

            const newToken = await refreshPromise
            isRefreshing = false
            refreshPromise = null

            // Retry request với token mới
            headers.set('Authorization', `Bearer ${newToken}`)
            response = await originalFetch(url, {
                ...options,
                headers,
                credentials: 'include',
            })
        } catch {
            isRefreshing = false
            refreshPromise = null
            // Refresh thất bại → xóa token, redirect login
            sessionStorage.removeItem('accessToken')
            sessionStorage.removeItem('refreshToken')
            sessionStorage.removeItem('user')
            // Chỉ redirect nếu chưa ở trang login
            if (!window.location.pathname.startsWith('/login')) {
                window.location.href = '/login'
            }
        }
    }

    return response
}

console.log('[FetchInterceptor] ✅ Đã kích hoạt — JWT token sẽ tự động gắn vào mọi API request')

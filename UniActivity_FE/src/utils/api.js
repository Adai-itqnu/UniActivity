let isRefreshing = false
let failedQueue = []

const processQueue = (error, token = null) => {
    failedQueue.forEach(prom => {
        if (error) {
            prom.reject(error)
        } else {
            prom.resolve(token)
        }
    })
    failedQueue = []
}

/**
 * Lấy access token từ sessionStorage
 */
export function getAccessToken() {
    return sessionStorage.getItem('accessToken')
}

/**
 * Lấy thông tin user đã lưu
 */
export function getStoredUser() {
    try {
        const user = sessionStorage.getItem('user')
        return user ? JSON.parse(user) : null
    } catch {
        return null
    }
}

/**
 * Kiểm tra user đã đăng nhập hay chưa
 */
export function isAuthenticated() {
    return !!getAccessToken()
}

/**
 * Xóa tokens và redirect về login
 */
export function logout() {
    sessionStorage.removeItem('accessToken')
    sessionStorage.removeItem('refreshToken')
    sessionStorage.removeItem('user')
    window.location.href = '/login'
}

/**
 * Refresh access token bằng refresh token
 */
async function refreshAccessToken() {
    const refreshToken = sessionStorage.getItem('refreshToken')
    if (!refreshToken) {
        throw new Error('No refresh token')
    }

    const response = await fetch('/api/auth/refresh', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
    })

    if (!response.ok) {
        throw new Error('Refresh failed')
    }

    const data = await response.json()
    sessionStorage.setItem('accessToken', data.accessToken)
    return data.accessToken
}


export async function apiFetch(url, options = {}) {
    const accessToken = getAccessToken()

    // Merge headers
    const headers = { ...options.headers }
    if (accessToken) {
        headers['Authorization'] = `Bearer ${accessToken}`
    }
    // Đặt Content-Type mặc định nếu chưa có và body không phải FormData
    if (!headers['Content-Type'] && options.body && !(options.body instanceof FormData)) {
        headers['Content-Type'] = 'application/json'
    }

    let response = await fetch(url, {
        ...options,
        headers,
        credentials: 'include', // Giữ credentials cho backward-compatible (SSE, OAuth2)
    })

    // Nếu nhận 401 → thử refresh token
    if (response.status === 401 && accessToken) {
        if (!isRefreshing) {
            isRefreshing = true
            try {
                const newToken = await refreshAccessToken()
                isRefreshing = false
                processQueue(null, newToken)

                // Retry request với token mới
                headers['Authorization'] = `Bearer ${newToken}`
                response = await fetch(url, {
                    ...options,
                    headers,
                    credentials: 'include',
                })
            } catch (err) {
                isRefreshing = false
                processQueue(err, null)
                // Refresh thất bại → logout
                logout()
                throw err
            }
        } else {
            // Đang refresh → đợi
            return new Promise((resolve, reject) => {
                failedQueue.push({
                    resolve: async (token) => {
                        headers['Authorization'] = `Bearer ${token}`
                        try {
                            const retryResponse = await fetch(url, {
                                ...options,
                                headers,
                                credentials: 'include',
                            })
                            resolve(retryResponse)
                        } catch (err) {
                            reject(err)
                        }
                    },
                    reject,
                })
            })
        }
    }

    return response
}

export default apiFetch

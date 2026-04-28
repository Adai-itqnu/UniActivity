/**
 * API Helper — Tự động gắn JWT token vào mọi request.
 * 
 * Thay vì dùng fetch() trực tiếp với credentials: 'include' (session cookie),
 * dùng apiFetch() để gắn Authorization: Bearer header.
 * 
 * Features:
 * - Tự động gắn accessToken vào header
 * - Tự động refresh token khi nhận 401
 * - Redirect về /login khi refresh token cũng hết hạn
 */

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
 * Lấy access token từ localStorage
 */
export function getAccessToken() {
    return localStorage.getItem('accessToken')
}

/**
 * Lấy thông tin user đã lưu
 */
export function getStoredUser() {
    try {
        const user = localStorage.getItem('user')
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
    localStorage.removeItem('accessToken')
    localStorage.removeItem('refreshToken')
    localStorage.removeItem('user')
    window.location.href = '/login'
}

/**
 * Refresh access token bằng refresh token
 */
async function refreshAccessToken() {
    const refreshToken = localStorage.getItem('refreshToken')
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
    localStorage.setItem('accessToken', data.accessToken)
    return data.accessToken
}

/**
 * Wrapper fetch() tự động gắn JWT token.
 * 
 * Sử dụng giống fetch() bình thường:
 *   const res = await apiFetch('/api/auth/me')
 *   const data = await res.json()
 * 
 * Hoặc:
 *   const res = await apiFetch('/admin/activities/api', {
 *     method: 'POST',
 *     body: JSON.stringify(data),
 *   })
 */
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

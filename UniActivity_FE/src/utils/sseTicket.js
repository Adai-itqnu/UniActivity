export async function requestSseTicket(fetchImpl = fetch) {
    const response = await fetchImpl('/sse/ticket', { method: 'POST' })
    const data = await response.json()
    if (!response.ok || !data.ticket) {
        throw new Error(data.error || 'Không thể tạo vé kết nối thời gian thực.')
    }
    return data.ticket
}

export function sseSubscribeUrl(ticket) {
    return `/sse/subscribe?ticket=${encodeURIComponent(ticket)}`
}

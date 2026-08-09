import { useEffect } from 'react'
import { showToast } from './toast'
import { requestSseTicket, sseSubscribeUrl } from './sseTicket'

const RETRY_DELAY_MS = 5000

function bindEventHandlers(eventSource, role) {
    eventSource.addEventListener('notification', (event) => {
        try {
            const payload = JSON.parse(event.data)
            showToast(payload.title || 'Thông báo mới', payload.message || '', 'success')
            window.dispatchEvent(new CustomEvent('new-notification', { detail: payload }))
        } catch (error) {
            console.error('[SSE] Failed to parse notification payload:', error)
        }
    })

    if (role === 'MANAGER') {
        eventSource.addEventListener('dashboard_update', (event) => {
            try {
                const payload = JSON.parse(event.data)
                window.dispatchEvent(new CustomEvent('dashboard-update', { detail: payload }))
            } catch (error) {
                console.error('[SSE] Failed to parse dashboard_update payload:', error)
            }
        })
    }

    eventSource.addEventListener('new_activity', (event) => {
        try {
            const payload = JSON.parse(event.data)
            window.dispatchEvent(new CustomEvent('new-activity', { detail: payload }))
        } catch (error) {
            console.error('[SSE] Failed to parse new_activity payload:', error)
        }
    })

    eventSource.addEventListener('activity_registration_update', (event) => {
        try {
            const payload = JSON.parse(event.data)
            window.dispatchEvent(new CustomEvent('activity-registration-update', { detail: payload }))
        } catch (error) {
            console.error('[SSE] Failed to parse activity_registration_update payload:', error)
        }
    })
}

export function useSseConnection(role) {
    useEffect(() => {
        if (!sessionStorage.getItem('accessToken')) return undefined

        let stopped = false
        let eventSource = null
        let retryTimer = null

        const scheduleReconnect = () => {
            if (!stopped) {
                retryTimer = window.setTimeout(connect, RETRY_DELAY_MS)
            }
        }

        const connect = async () => {
            try {
                const ticket = await requestSseTicket()
                if (stopped) return

                eventSource = new EventSource(sseSubscribeUrl(ticket))
                eventSource.onopen = () => {
                    console.log(`[SSE] Connected for role: ${role}`)
                }
                eventSource.onerror = (error) => {
                    console.warn('[SSE] Connection closed; requesting a fresh ticket.', error)
                    eventSource?.close()
                    eventSource = null
                    scheduleReconnect()
                }
                bindEventHandlers(eventSource, role)
            } catch (error) {
                console.warn('[SSE] Unable to obtain purpose ticket.', error)
                scheduleReconnect()
            }
        }

        connect()

        return () => {
            stopped = true
            if (retryTimer !== null) window.clearTimeout(retryTimer)
            eventSource?.close()
        }
    }, [role])
}

export default useSseConnection

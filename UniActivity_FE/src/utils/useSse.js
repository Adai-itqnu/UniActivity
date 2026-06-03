import { useEffect } from 'react'
import { showToast } from './toast'

/**
 * Custom hook to manage real-time Server-Sent Events (SSE) subscriptions.
 * Establishes a persistent EventSource connection, handles auth query param,
 * listens to incoming events, displays Toast alerts, and fires custom browser events.
 */
export function useSseConnection(role) {
    useEffect(() => {
        const accessToken = sessionStorage.getItem('accessToken');
        if (!accessToken) return;

        console.log(`[SSE] 🔌 Connecting to SSE subscription for role: ${role}`);
        const eventSource = new EventSource(`/sse/subscribe?token=${accessToken}`);

        eventSource.onopen = () => {
            console.log(`[SSE] 🟢 Connected to SSE subscription successfully`);
        };

        eventSource.onerror = (err) => {
            console.warn('[SSE] ⚠️ SSE connection closed or error occurred. Reconnecting...', err);
        };

        // 1. Listen to generic notifications
        eventSource.addEventListener('notification', (e) => {
            try {
                const payload = JSON.parse(e.data);
                console.log('[SSE] 🔔 Received real-time notification:', payload);
                
                // Show floating glassmorphism toast
                showToast(payload.title || 'Thông báo mới', payload.message || '', 'success');

                // Dispatch global custom event for layout headers to fetch unread count/list
                window.dispatchEvent(new CustomEvent('new-notification', { detail: payload }));
            } catch (err) {
                console.error('[SSE] Failed to parse notification payload:', err);
            }
        });

        // 2. Listen to dashboard updates (only for MANAGERS)
        if (role === 'MANAGER') {
            eventSource.addEventListener('dashboard_update', (e) => {
                try {
                    const payload = JSON.parse(e.data);
                    console.log('[SSE] 📊 Received real-time dashboard update:', payload);

                    // Dispatch global custom event for manager dashboard to trigger silent re-fetch
                    window.dispatchEvent(new CustomEvent('dashboard-update', { detail: payload }));
                } catch (err) {
                    console.error('[SSE] Failed to parse dashboard_update payload:', err);
                }
            });
        }

        // 3. Listen to new activity events (for STUDENT and MANAGER)
        eventSource.addEventListener('new_activity', (e) => {
            try {
                const payload = JSON.parse(e.data);
                console.log('[SSE] 🆕 Received new activity event:', payload);

                // Dispatch global custom event for activities pages to re-fetch
                window.dispatchEvent(new CustomEvent('new-activity', { detail: payload }));
            } catch (err) {
                console.error('[SSE] Failed to parse new_activity payload:', err);
            }
        });

        // 4. Listen to activity registration updates (for MANAGERS)
        eventSource.addEventListener('activity_registration_update', (e) => {
            try {
                const payload = JSON.parse(e.data);
                console.log('[SSE] 📝 Received activity registration update:', payload);

                // Dispatch global custom event for manager activity detail to re-fetch registrations
                window.dispatchEvent(new CustomEvent('activity-registration-update', { detail: payload }));
            } catch (err) {
                console.error('[SSE] Failed to parse activity_registration_update payload:', err);
            }
        });

        // Cleanup connection on unmount
        return () => {
            console.log(`[SSE] 🔌 Closing SSE connection for role: ${role}`);
            eventSource.close();
        };
    }, [role]);
}

export default useSseConnection;

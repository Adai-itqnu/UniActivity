const TOAST_TYPES = new Set(['info', 'success', 'warning', 'error'])

const TYPE_CONFIG = {
    info: {
        icon: 'notifications',
        iconClass: 'bg-blue-500/10 text-blue-500',
        borderClass: 'border-l-blue-500',
    },
    success: {
        icon: 'check_circle',
        iconClass: 'bg-emerald-500/10 text-emerald-500',
        borderClass: 'border-l-emerald-500',
    },
    warning: {
        icon: 'warning',
        iconClass: 'bg-amber-500/10 text-amber-500',
        borderClass: 'border-l-amber-500',
    },
    error: {
        icon: 'error',
        iconClass: 'bg-rose-500/10 text-rose-500',
        borderClass: 'border-l-rose-500',
    },
}

export function normalizeToast({ title, message, type }) {
    return {
        title: String(title ?? ''),
        message: String(message ?? ''),
        type: TOAST_TYPES.has(type) ? type : 'info',
    }
}

function createElement(tagName, className, text) {
    const element = document.createElement(tagName)
    element.className = className
    if (text !== undefined) element.textContent = text
    return element
}

export function showToast(title, message, type = 'info') {
    const normalized = normalizeToast({ title, message, type })
    const config = TYPE_CONFIG[normalized.type]

    let container = document.getElementById('toast-container')
    if (!container) {
        container = createElement(
            'div',
            'fixed bottom-5 right-5 z-[9999] flex flex-col gap-3 max-w-sm w-full pointer-events-none',
        )
        container.id = 'toast-container'
        document.body.appendChild(container)
    }

    const toast = createElement(
        'div',
        `translate-x-12 opacity-0 pointer-events-auto flex items-start gap-3 p-4 rounded-2xl border border-l-4 ${config.borderClass} bg-white/80 dark:bg-slate-900/80 backdrop-blur-md shadow-2xl transition-all duration-300 ease-out border-slate-200/50 dark:border-slate-800/50`,
    )
    const iconWrapper = createElement(
        'div',
        `size-10 rounded-xl ${config.iconClass} flex items-center justify-center shrink-0 shadow-inner`,
    )
    iconWrapper.appendChild(createElement(
        'span',
        'material-symbols-outlined text-[20px]',
        config.icon,
    ))

    const content = createElement('div', 'flex-1 min-w-0')
    content.appendChild(createElement(
        'h4',
        'text-sm font-bold text-slate-800 dark:text-slate-200 leading-tight',
        normalized.title,
    ))
    content.appendChild(createElement(
        'p',
        'text-xs text-slate-500 dark:text-slate-400 mt-1 leading-normal',
        normalized.message,
    ))

    const closeButton = createElement(
        'button',
        'text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 transition-colors shrink-0',
    )
    closeButton.type = 'button'
    closeButton.setAttribute('aria-label', 'Đóng thông báo')
    closeButton.appendChild(createElement(
        'span',
        'material-symbols-outlined text-[16px]',
        'close',
    ))

    toast.append(iconWrapper, content, closeButton)
    container.appendChild(toast)

    requestAnimationFrame(() => {
        toast.classList.remove('translate-x-12', 'opacity-0')
    })

    const autoClose = setTimeout(() => removeToast(), 5000)
    const removeToast = () => {
        clearTimeout(autoClose)
        toast.classList.add('translate-x-12', 'opacity-0')
        setTimeout(() => toast.remove(), 300)
    }
    closeButton.addEventListener('click', removeToast)
}

export default showToast

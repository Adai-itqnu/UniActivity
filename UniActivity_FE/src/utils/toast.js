export function showToast(title, message, type = 'info') {
    // 1. Create container if it doesn't exist
    let container = document.getElementById('toast-container');
    if (!container) {
        container = document.createElement('div');
        container.id = 'toast-container';
        container.className = 'fixed bottom-5 right-5 z-[9999] flex flex-col gap-3 max-w-sm w-full pointer-events-none';
        document.body.appendChild(container);
    }

    // 2. Create toast element
    const toast = document.createElement('div');
    toast.className = 'translate-x-12 opacity-0 pointer-events-auto flex items-start gap-3 p-4 rounded-2xl border bg-white/80 dark:bg-slate-900/80 backdrop-blur-md shadow-2xl transition-all duration-300 ease-out border-slate-200/50 dark:border-slate-800/50';

    // Type styling
    let icon = 'notifications';
    let iconBg = 'bg-slate-500/10 text-slate-500';
    if (type === 'success') {
        icon = 'check_circle';
        iconBg = 'bg-emerald-500/10 text-emerald-500';
        toast.className += ' border-l-4 border-l-emerald-500';
    } else if (type === 'error') {
        icon = 'error';
        iconBg = 'bg-rose-500/10 text-rose-500';
        toast.className += ' border-l-4 border-l-rose-500';
    } else if (type === 'warning') {
        icon = 'warning';
        iconBg = 'bg-amber-500/10 text-amber-500';
        toast.className += ' border-l-4 border-l-amber-500';
    } else {
        iconBg = 'bg-blue-500/10 text-blue-500';
        toast.className += ' border-l-4 border-l-blue-500';
    }

    toast.innerHTML = `
        <div class="size-10 rounded-xl ${iconBg} flex items-center justify-center shrink-0 shadow-inner">
            <span class="material-symbols-outlined text-[20px]">${icon}</span>
        </div>
        <div class="flex-1 min-w-0">
            <h4 class="text-sm font-bold text-slate-800 dark:text-slate-200 leading-tight">${title}</h4>
            <p class="text-xs text-slate-500 dark:text-slate-400 mt-1 leading-normal">${message}</p>
        </div>
        <button class="text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 transition-colors shrink-0">
            <span class="material-symbols-outlined text-[16px]">close</span>
        </button>
    `;

    // Close button action
    const closeBtn = toast.querySelector('button');
    closeBtn.onclick = () => removeToast(toast);

    container.appendChild(toast);

    // Trigger animate-in
    requestAnimationFrame(() => {
        toast.classList.remove('translate-x-12', 'opacity-0');
    });

    // Auto-remove after 5 seconds
    const autoClose = setTimeout(() => {
        removeToast(toast);
    }, 5000);

    function removeToast(el) {
        clearTimeout(autoClose);
        el.classList.add('translate-x-12', 'opacity-0');
        setTimeout(() => {
            if (el.parentNode) {
                el.parentNode.removeChild(el);
            }
        }, 300);
    }
}

export default showToast;

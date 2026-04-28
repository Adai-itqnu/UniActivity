import { useRef } from 'react'

/**
 * DateTimeInput — wrapper trên <input type="datetime-local"> 
 * Giữ nguyên giao diện native picker nhưng hiển thị dd/MM/yyyy HH:mm
 */
export default function DateTimeInput({ value, onChange, className = '' }) {
    const inputRef = useRef(null)

    const formatDisplay = (v) => {
        if (!v) return ''
        try {
            const [datePart, timePart] = v.split('T')
            const [y, m, d] = datePart.split('-')
            const time = timePart || '00:00'
            return `${d}/${m}/${y} ${time}`
        } catch {
            return v
        }
    }

    return (
        <div className="relative">
            {/* Hidden native input — chỉ dùng để mở picker */}
            <input
                ref={inputRef}
                type="datetime-local"
                value={value}
                onChange={onChange}
                className="absolute inset-0 w-full h-full opacity-0 cursor-pointer z-10"
            />
            {/* Visible display — dd/MM/yyyy HH:mm, dùng class gốc từ parent */}
            <div className={`${className} flex items-center justify-between pointer-events-none`}>
                {value ? (
                    <span>{formatDisplay(value)}</span>
                ) : (
                    <span className="text-gray-400">dd/mm/yyyy --:--</span>
                )}
                <span className="material-symbols-outlined text-gray-400 text-base">calendar_today</span>
            </div>
        </div>
    )
}

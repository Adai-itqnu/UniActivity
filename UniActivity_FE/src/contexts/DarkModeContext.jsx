import { createContext, useContext, useState, useEffect } from 'react'

const DarkModeContext = createContext()

export function DarkModeProvider({ children }) {
    const [isDark, setIsDark] = useState(() => {
        // Lấy trạng thái đã lưu từ localStorage
        const saved = localStorage.getItem('uniactivity-dark-mode')
        return saved === 'true'
    })

    useEffect(() => {
        // Thêm/xóa class 'dark' trên <html> element
        if (isDark) {
            document.documentElement.classList.add('dark')
        } else {
            document.documentElement.classList.remove('dark')
        }
        // Lưu vào localStorage để dùng cho tất cả các trang
        localStorage.setItem('uniactivity-dark-mode', isDark)
    }, [isDark])

    const toggleDarkMode = () => setIsDark(!isDark)

    return (
        <DarkModeContext.Provider value={{ isDark, toggleDarkMode }}>
            {children}
        </DarkModeContext.Provider>
    )
}

export function useDarkMode() {
    const context = useContext(DarkModeContext)
    if (!context) {
        throw new Error('useDarkMode must be used within a DarkModeProvider')
    }
    return context
}

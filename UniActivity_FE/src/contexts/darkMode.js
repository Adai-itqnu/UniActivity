import { createContext, useContext } from 'react'

export const DarkModeContext = createContext(null)

export function useDarkMode() {
    const context = useContext(DarkModeContext)
    if (!context) {
        throw new Error('useDarkMode must be used within a DarkModeProvider')
    }
    return context
}

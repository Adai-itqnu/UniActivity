import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    proxy: {
      // Proxy POST /login tới Spring Boot backend (chỉ POST, không GET)
      '/login': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        // Chỉ proxy POST requests (form login), GET /login sẽ do React xử lý
        bypass: (req) => {
          if (req.method === 'GET') {
            return req.url // Trả về React app cho GET requests
          }
        },
      },
      // Proxy POST /register tới Spring Boot backend
      '/register': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        bypass: (req) => {
          if (req.method === 'GET') {
            return req.url
          }
        },
      },
      // Proxy tất cả API endpoints
      '/admin': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/manager': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/student': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/sse': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/oauth2': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/logout': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})

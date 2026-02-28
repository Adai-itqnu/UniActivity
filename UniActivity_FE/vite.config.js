import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    proxy: {
      // Proxy OAuth2 callback (luôn proxy, không bypass)
      '/login/oauth2': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        cookieDomainRewrite: '',
        cookiePathRewrite: '/',
      },
      // Proxy POST /login tới Spring Boot backend (chỉ POST, không GET)
      '/login': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        cookieDomainRewrite: '',
        cookiePathRewrite: '/',
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
        cookieDomainRewrite: '',
        cookiePathRewrite: '/',
        bypass: (req) => {
          if (req.method === 'GET') {
            return req.url
          }
        },
      },
      // Proxy admin API endpoints tới Spring Boot backend
      // GET requests trang admin sẽ do React SPA xử lý
      '/admin': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        cookieDomainRewrite: '',
        cookiePathRewrite: '/',
        bypass: (req) => {
          // Chỉ proxy các request tới API endpoints, không proxy trang HTML
          if (req.method === 'GET' && !req.url.includes('/api')) {
            return req.url // Trả về React app
          }
        },
      },
      '/manager': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        cookieDomainRewrite: '',
        cookiePathRewrite: '/',
        bypass: (req) => {
          // Chỉ proxy API requests, GET trang sẽ do React SPA xử lý
          if (req.method === 'GET' && !req.url.includes('/api')) {
            return req.url
          }
        },
      },
      '/student': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        cookieDomainRewrite: '',
        cookiePathRewrite: '/',
        bypass: (req) => {
          // Chỉ proxy API requests, GET trang sẽ do React SPA xử lý
          if (req.method === 'GET' && !req.url.includes('/api')) {
            return req.url
          }
        },
      },
      '/sse': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        cookieDomainRewrite: '',
        cookiePathRewrite: '/',
      },
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        cookieDomainRewrite: '',
        cookiePathRewrite: '/',
      },
      '/oauth2': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        cookieDomainRewrite: '',
        cookiePathRewrite: '/',
      },
      '/logout': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        cookieDomainRewrite: '',
        cookiePathRewrite: '/',
      },
      // Proxy uploaded files (banner images, evidence, etc.)
      '/uploads': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})

import tailwindcss from '@tailwindcss/vite'
import basicSsl from '@vitejs/plugin-basic-ssl'
import vue from '@vitejs/plugin-vue'
import { defineConfig, loadEnv } from 'vite'

// The dev server proxies the Java backend so the whole app is served from ONE origin.
// That lets phones on the same Wi-Fi open http(s)://<your-pc-ip>:5173 and still reach
// the API and WebSocket server without any extra configuration.
//
//   npm run dev        -> http  (QR camera works on localhost; phones can use "upload photo")
//   npm run dev:https  -> https (needed for live phone-camera scanning over the LAN)
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const apiTarget = env.API_PROXY_TARGET || 'http://localhost:8080'
  const wsTarget = env.WS_PROXY_TARGET || 'ws://localhost:8081'
  const useHttps = mode === 'https' || env.DEV_HTTPS === 'true'

  return {
    plugins: [vue(), tailwindcss(), ...(useHttps ? [basicSsl()] : [])],
    server: {
      host: true,
      port: 5173,
      proxy: {
        '/api': { target: apiTarget, changeOrigin: true, xfwd: true },
        '/uploads': { target: apiTarget, changeOrigin: true },
        '/ws': { target: wsTarget, ws: true, rewrite: (path) => path.replace(/^\/ws/, '') },
      },
    },
    preview: {
      host: true,
      port: 4173,
      proxy: {
        '/api': { target: apiTarget, changeOrigin: true, xfwd: true },
        '/uploads': { target: apiTarget, changeOrigin: true },
        '/ws': { target: wsTarget, ws: true, rewrite: (path) => path.replace(/^\/ws/, '') },
      },
    },
    build: {
      chunkSizeWarningLimit: 800,
    },
  }
})

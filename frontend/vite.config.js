import tailwindcss from '@tailwindcss/vite'
import basicSsl from '@vitejs/plugin-basic-ssl'
import vue from '@vitejs/plugin-vue'
import { defineConfig, loadEnv } from 'vite'

// Both dev servers proxy the Java backend; HTTP supports local browsing and HTTPS
// enables live camera access from phones on the same Wi-Fi.
//
//   npm run dev        -> http  (default port 5173)
//   npm run dev:https  -> https (port 5174 when started by run.sh)
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

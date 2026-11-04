import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', 'VITE_')
  const backendUrl = env.VITE_BACKEND_URL || 'http://localhost:8080'

  return {
    plugins: [react()],
    server: {
      port: 5173,
      allowedHosts: env.VITE_PUBLIC_HOST ? [env.VITE_PUBLIC_HOST] : [],
      proxy: {
        '/mercadolivre/callback': {
          target: backendUrl,
          changeOrigin: true,
        },
        '/api': {
          target: backendUrl,
          changeOrigin: true,
          rewrite: path => path.replace(/^\/api/, ''),
        },
      },
    },
  }
})

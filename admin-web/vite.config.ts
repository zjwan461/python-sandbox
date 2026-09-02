import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// admin-web 工程配置（T-0011，design.md §9.1）
// dev 模式下 /admin-api 代理至 admin-server（默认 9090）；生产构建后由静态服务反代同路径。
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5173,
    proxy: {
      '/admin-api': {
        target: process.env.VITE_PROXY_TARGET || 'http://localhost:9090',
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: 'dist',
    chunkSizeWarningLimit: 1500
  }
})

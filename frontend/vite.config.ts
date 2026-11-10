import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  css: {
    preprocessorOptions: {
      // resume-design 的模板组件使用 SCSS；modern-compiler 避免 legacy API 警告
      scss: { api: 'modern-compiler' },
    },
  },
  server: {
    port: 5173,
    // 开发环境把 /api 请求代理到后端，避免跨域问题
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})

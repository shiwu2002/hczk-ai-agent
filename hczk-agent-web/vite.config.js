import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    tailwindcss(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    },
  },
  build: {
    // 分包策略：将第三方依赖独立打包，利用浏览器缓存
    // - vue 生态（vue + vue-router + pinia）单独分包
    // - chart.js + vue-chartjs 单独分包（仅 Dashboard 等页面使用）
    // - lucide 图标库单独分包
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes('node_modules')) {
            if (id.includes('vue') || id.includes('pinia') || id.includes('vue-router')) {
              return 'vue-vendor'
            }
            if (id.includes('chart.js') || id.includes('chartjs-adapter') || id.includes('vue-chartjs')) {
              return 'chart-vendor'
            }
            if (id.includes('lucide-vue-next')) {
              return 'icons-vendor'
            }
          }
        },
      },
    },
    // 生产构建关闭 sourcemap，减小产物体积
    sourcemap: false,
    // 警告阈值 1000KB
    chunkSizeWarningLimit: 1000,
  },
})

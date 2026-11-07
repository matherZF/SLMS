import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

export default defineConfig({
  plugins: [vue()],
  // 构建时间戳：打进前端产物，用于区分手机上装的是哪个包（个人信息页可见）
  define: {
    __BUILD_TIME__: JSON.stringify(
      new Date().toLocaleString('zh-CN', { hour12: false })
    )
  },
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src')
    }
  },
  server: {
    port: 5173,
    host: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },
  css: {
    preprocessorOptions: {
      scss: {
        additionalData: `@use "@/styles/variables.scss" as *;`
      }
    }
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) return
          if (id.includes('element-plus') || id.includes('@element-plus')) return 'element-plus'
          if (id.includes('/vue/') || id.includes('vue-router') || id.includes('pinia')) return 'vue-vendor'
        }
      }
    }
  }
})

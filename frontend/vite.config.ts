import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

const apiProxyTarget = process.env.VITE_API_PROXY_TARGET || 'http://localhost:8090'
const basePath = process.env.VITE_BASE_PATH || '/'
const apiProxy = {
  '/api': {
    target: apiProxyTarget,
    changeOrigin: true,
  },
}

export default defineConfig({
  base: basePath,
  plugins: [vue()],
  build: {
    chunkSizeWarningLimit: 1500,
    rollupOptions: {
      output: {
        assetFileNames(assetInfo) {
          const name = assetInfo.name ?? 'asset'
          return name.endsWith('.css') ? 'assets/[name][extname]' : 'assets/[name]-[hash][extname]'
        },
        manualChunks(id) {
          if (id.includes('node_modules/ant-design-vue') || id.includes('node_modules/@ant-design/icons-vue')) {
            return 'vendor-antd'
          }
          if (id.includes('node_modules/vue-router') || id.includes('node_modules/pinia') || id.includes('node_modules/vue')) {
            return 'vendor-vue'
          }
          if (id.includes('node_modules/axios')) return 'vendor-http'
          if (id.includes('node_modules/dayjs')) return 'vendor-date'
        },
      },
    },
  },
  server: {
    port: 5174,
    proxy: apiProxy,
  },
  preview: {
    port: 4174,
    proxy: apiProxy,
  },
})

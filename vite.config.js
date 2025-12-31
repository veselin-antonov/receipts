import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react-swc';
import fs from 'fs';
import path from 'path';
import { visualizer } from 'rollup-plugin-visualizer';
import { defineConfig } from 'vite';

export default defineConfig({
  plugins: [
    react(),
    tailwindcss(),
    visualizer({
      open: true,
      gzipSize: true,
      brotliSize: true,
    }),
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  build: {
    chunkSizeWarningLimit: 1000, // Suppress warnings for chunks under 1MB
    rollupOptions: {
      output: {
        manualChunks(id) {
          // React ecosystem - split into own chunk (includes scheduler)
          if (
            id.includes('node_modules/react') ||
            id.includes('node_modules/scheduler')
          ) {
            return 'react-vendor';
          }

          // React Router - frequently lazy-loadable
          if (id.includes('node_modules/react-router')) {
            return 'router-vendor';
          }

          // Form libraries
          if (
            id.includes('node_modules/react-hook-form') ||
            id.includes('node_modules/@hookform')
          ) {
            return 'form-vendor';
          }

          // UI and styling
          if (
            id.includes('node_modules/@radix-ui') ||
            id.includes('node_modules/lucide-react') ||
            id.includes('node_modules/cmdk')
          ) {
            return 'ui-vendor';
          }

          // Date utilities
          if (
            id.includes('node_modules/date-fns') ||
            id.includes('node_modules/react-day-picker')
          ) {
            return 'date-vendor';
          }

          // Utility libraries
          if (
            id.includes('node_modules/clsx') ||
            id.includes('node_modules/class-variance-authority') ||
            id.includes('node_modules/tailwind-merge')
          ) {
            return 'utils-vendor';
          }

          // Everything else in node_modules
          if (id.includes('node_modules')) {
            return 'other-vendor';
          }
        },
      },
    },
  },
  server: {
    https:
      fs.existsSync('./localhost.key') && fs.existsSync('./localhost.crt')
        ? {
            key: fs.readFileSync('./localhost.key'),
            cert: fs.readFileSync('./localhost.crt'),
          }
        : undefined,
    proxy: {
      '/api': {
        target: 'https://localhost:7002',
        changeOrigin: true,
        secure: false,
      },
    },
  },
});

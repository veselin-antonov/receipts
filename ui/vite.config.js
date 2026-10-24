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
      filename: './dist/stats.html',
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
  },
  test: {
    environment: 'jsdom',
    setupFiles: './src/setupTests.js',
  },
  server: {
    // The dev port block (ROADMAP M1b decision 21): vite 7800, the api 7802
    port: 7800,
    strictPort: true,
    https:
      fs.existsSync('./localhost.key') && fs.existsSync('./localhost.crt')
        ? {
            key: fs.readFileSync('./localhost.key'),
            cert: fs.readFileSync('./localhost.crt'),
          }
        : undefined,
    proxy: {
      '/api': {
        target: 'http://localhost:7802',
        changeOrigin: true,
        secure: false,
      },
    },
  },
});

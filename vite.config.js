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

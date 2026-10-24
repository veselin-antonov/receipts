import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react-swc';
import fs from 'fs';
import path from 'path';
import { visualizer } from 'rollup-plugin-visualizer';
import { defineConfig, loadEnv } from 'vite';

// The repository root's .env, the one bootRun and compose.dev.yaml read. The
// empty prefix loads every variable, for this file only: the browser bundle
// still sees only VITE_* variables.
const env = loadEnv(
  process.env.NODE_ENV ?? 'development',
  path.resolve(__dirname, '..'),
  ''
);
const apiTarget =
  env.UI_API_TARGET || `http://localhost:${env.SERVER_PORT || 7802}`;
const allowedHosts = (env.UI_ALLOWED_HOSTS || 'receipts-dev.vasoft.dev')
  .split(',')
  .map((host) => host.trim())
  .filter(Boolean);

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
    // Hosts a reverse proxy serves vite under. Hot reload needs nothing more
    // than WebSockets on that proxy host: the client connects back to the
    // page's own host and port.
    allowedHosts,
    https:
      fs.existsSync('./localhost.key') && fs.existsSync('./localhost.crt')
        ? {
            key: fs.readFileSync('./localhost.key'),
            cert: fs.readFileSync('./localhost.crt'),
          }
        : undefined,
    proxy: {
      '/api': {
        target: apiTarget,
        changeOrigin: true,
        secure: false,
      },
    },
  },
});

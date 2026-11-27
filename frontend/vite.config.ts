import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import path from 'path';

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const apiBaseUrl = env.VITE_API_BASE_URL?.trim();

  if (mode === 'production' && !apiBaseUrl) {
    throw new Error(
      'VITE_API_BASE_URL is required for production builds. Set VITE_API_BASE_URL before running "npm run build".'
    );
  }

  return {
    plugins: [react(), tailwindcss()],
    define:
      mode !== 'production' && !apiBaseUrl
        ? { 'import.meta.env.VITE_API_BASE_URL': JSON.stringify('http://localhost:8080/api/v1') }
        : {},
    resolve: {
      alias: {
        '@': path.resolve(__dirname, './src'),
      },
    },
  };
});

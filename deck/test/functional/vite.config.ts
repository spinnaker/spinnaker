import path from 'path';
import autoprefixer from 'autoprefixer';
import { defineConfig } from 'vite';

export default defineConfig({
  css: {
    postcss: {
      plugins: [autoprefixer()],
    },
  },
  preview: {
    port: 5173,
    strictPort: true,
  },
  build: {
    outDir: path.resolve(__dirname, '../../build/webpack'),
    emptyOutDir: false,
  },
});

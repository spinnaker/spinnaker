import path from 'path';
import { defineConfig } from 'vite';

export default defineConfig({
  preview: {
    port: 5173,
    strictPort: true,
  },
  build: {
    outDir: path.resolve(__dirname, '../../build/webpack'),
    emptyOutDir: false,
  },
});

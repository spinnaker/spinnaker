import { createRequire } from 'module';
import path from 'path';
import autoprefixer from 'autoprefixer';
import { defineConfig, loadEnv } from 'vite';

const APP_ROOT = path.resolve(__dirname, '../../packages/app');
const appRequire = createRequire(path.join(__dirname, 'package.json'));
const { mergeViteEnvironment } = appRequire('../../packages/app/scripts/vite-environment.js') as {
  mergeViteEnvironment: (
    loadEnvironment: typeof loadEnv,
    mode: string,
    appRoot: string,
    processEnvironment: NodeJS.ProcessEnv,
  ) => Record<string, string | undefined>;
};
const { resolveBuildOutput } = appRequire('../../packages/app/scripts/vite-output-path.js') as {
  resolveBuildOutput: (appRoot: string, env: Record<string, string | undefined>) => string;
};
const env = mergeViteEnvironment(loadEnv, 'production', APP_ROOT, process.env);

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
    outDir: resolveBuildOutput(APP_ROOT, env),
    emptyOutDir: false,
  },
});

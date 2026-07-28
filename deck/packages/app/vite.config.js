import reactRefresh from '@vitejs/plugin-react-refresh';
import dotenv from 'dotenv';
import fs from 'fs';
import { createRequire } from 'module';
import path from 'path';
import strip from 'rollup-plugin-strip-code';
import { defineConfig } from 'vite';
import htmlConfigPlugin from 'vite-plugin-html-config';
import svgr from 'vite-plugin-svgr';

const DECK_ROOT = path.resolve(`${__dirname}/../../`);
const require = createRequire(import.meta.url);
const CORE_PACKAGE_ROOT = path.dirname(require.resolve('@spinnaker/core/package.json', { paths: [__dirname] }));

const envLocalFilePath = path.resolve(`${__dirname}/.env.local`);
if (fs.existsSync(envLocalFilePath)) {
  dotenv.config({
    path: envLocalFilePath,
  });
}

export default defineConfig(({ command }) => {
  const alias = [
    { find: 'root', replacement: DECK_ROOT },
    {
      find: 'coreImports',
      replacement: `${CORE_PACKAGE_ROOT}/src/presentation/less/imports/commonImports.less`,
    },
  ];

  if (command === 'serve') {
    // During development directly use source files from linked packages rather than build output.
    alias.push({
      find: '@spinnaker/core',
      replacement: `${DECK_ROOT}/packages/core/src/index.ts`,
    });
  }
  return {
    clearScreen: false,
    plugins: [
      reactRefresh(),
      htmlConfigPlugin(
        command === 'build' ? { favicon: 'icons/prod-favicon.ico' } : { favicon: 'icons/dev-favicon.ico' },
      ),
      strip({
        exclude: /node_modules/,
        pattern: new RegExp(
          `([\\t ]*\\/\\*! ?Start - Rollup Remove ?\\*\\/)[\\s\\S]*?(\\/\\*! ?End - Rollup Remove ?\\*\\/[\\t ]*\\n?)`,
          'g',
        ),
      }),
      svgr(),
    ],
    resolve: {
      alias,
      mainFields: ['module', 'jsnext:main', 'jsnext', 'main:esnext'],
    },
    server: {
      host: process.env.DECK_HOST,
      // See https://github.com/vitejs/vite/pull/3895 for details on the config.
      https: process.env.DECK_HTTPS === 'true' ? { maxSessionMemory: 100, peerMaxConcurrentStreams: 300 } : false,
      port: 9000,
    },
  };
});

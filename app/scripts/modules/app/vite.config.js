import reactRefresh from '@vitejs/plugin-react-refresh';
import dotenv from 'dotenv';
import fs from 'fs';
import path from 'path';
import strip from 'rollup-plugin-strip-code';
import { defineConfig } from 'vite';
import htmlConfigPlugin from 'vite-plugin-html-config';
import svgr from 'vite-plugin-svgr';

import angularTemplateLoader from '@spinnaker/scripts/helpers/rollup-plugin-angularjs-template-loader';

const DECK_ROOT = path.resolve(`${__dirname}/../../../..`);
const NODE_MODULE_PATH = path.resolve(`${DECK_ROOT}/node_modules`);

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
      replacement: `${NODE_MODULE_PATH}/@spinnaker/core/src/presentation/less/imports/commonImports.less`,
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
    // `vite` has a depdendency optimization step where it pre-bundles the dependencies using esbuild and directly
    // serves the source files. When `vite` encounters linked packages, it doesn't include them in the pre-bundle and
    // instead treats them as source files. However (not sure it is intentional or a bug), it still runs esbuild across
    // the linked package source files (for building the module graph?). This is an issue when we have custom loaders
    // defined as rollup plugins since this will not be used in this step.
    // So fixing the issue by making esbuild load .html files as text files (which is ok since it doesn't affect the
    // output) and later use rollup to actually load/transform the file.
    optimizeDeps: {
      esbuildOptions: {
        loader: {
          '.html': 'text',
        },
      },
    },
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
      angularTemplateLoader({ sourceMap: true }),
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

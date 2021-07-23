import dotenv from 'dotenv';
import fs from 'fs';
import path from 'path';
import strip from 'rollup-plugin-strip-code';
import { defineConfig } from 'vite';

import angularTemplateLoader from '@spinnaker/scripts/helpers/rollup-plugin-angularjs-template-loader';

const DECK_ROOT = path.resolve(`${__dirname}/../../../..`);
const NODE_MODULE_PATH = path.resolve(`${DECK_ROOT}/node_modules`);

const envLocalFilePath = path.resolve(`${__dirname}/.env.local`);
if (fs.existsSync(envLocalFilePath)) {
  dotenv.config({
    path: envLocalFilePath,
  });
}

export default defineConfig({
  clearScreen: false,
  plugins: [
    strip({
      exclude: /node_modules/,
      pattern: new RegExp(
        `([\\t ]*\\/\\*! ?Start - Rollup Remove ?\\*\\/)[\\s\\S]*?(\\/\\*! ?End - Rollup Remove ?\\*\\/[\\t ]*\\n?)`,
        'g',
      ),
    }),
    angularTemplateLoader({ sourceMap: true }),
  ],
  resolve: {
    alias: [
      { find: 'root', replacement: DECK_ROOT },
      {
        find: 'coreImports',
        replacement: `${NODE_MODULE_PATH}/@spinnaker/core/src/presentation/less/imports/commonImports.less`,
      },
    ],
    mainFields: ['module', 'jsnext:main', 'jsnext', 'main:esnext'],
  },
  server: {
    host: process.env.DECK_HOST,
    // See https://github.com/vitejs/vite/pull/3895 for details on the config.
    https: process.env.DECK_HTTPS === 'true' ? { maxSessionMemory: 100, peerMaxConcurrentStreams: 300 } : false,
    port: 9000,
  },
});

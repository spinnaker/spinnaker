import basicSsl from '@vitejs/plugin-basic-ssl';
import react from '@vitejs/plugin-react';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { defineConfig, loadEnv, normalizePath } from 'vite';
import type { Plugin, PluginOption } from 'vite';
import { viteStaticCopy } from 'vite-plugin-static-copy';

import {
  buildPublicEnvDefines,
  getTlsMode,
  resolveBuildOutput,
  resolveSettingsPath,
  staticCopyTargets,
} from './vite.config.helpers';

const APP_ROOT = path.dirname(fileURLToPath(import.meta.url));
const DECK_ROOT = path.resolve(APP_ROOT, '../..');
const require = createRequire(import.meta.url);
const { mergeViteEnvironment } = require('./scripts/vite-environment.js') as {
  mergeViteEnvironment: (
    loadEnvironment: typeof loadEnv,
    mode: string,
    appRoot: string,
    processEnvironment: NodeJS.ProcessEnv,
  ) => Record<string, string | undefined>;
};

function resolvePackageRoot(packageName: string, searchRoot = APP_ROOT): string {
  return path.dirname(require.resolve(`${packageName}/package.json`, { paths: [searchRoot] }));
}

const CORE_PACKAGE_ROOT = resolvePackageRoot('@spinnaker/core');
const STYLEGUIDE_PACKAGE_ROOT = resolvePackageRoot('@spinnaker/styleguide');
const SETTINGS_LOCAL_PATH = path.resolve(APP_ROOT, 'src/settings-local.js');
const SHARED_PACKAGE_NAMES = ['@uirouter/core', '@uirouter/react', 'react', 'react-dom'];
const SHARED_PACKAGE_ROOTS = Object.fromEntries(
  SHARED_PACKAGE_NAMES.map((packageName) => [packageName, resolvePackageRoot(packageName, CORE_PACKAGE_ROOT)]),
);
const PROVIDER_PACKAGE_ROOTS = [
  '@spinnaker/amazon',
  '@spinnaker/appengine',
  '@spinnaker/azure',
  '@spinnaker/cloudfoundry',
  '@spinnaker/cloudrun',
  '@spinnaker/docker',
  '@spinnaker/ecs',
  '@spinnaker/google',
  '@spinnaker/kubernetes',
  '@spinnaker/oracle',
].map((packageName) => normalizePath(path.join(resolvePackageRoot(packageName), 'dist/')));
const REACT_VIRTUALIZED_COMMONJS = require.resolve('react-virtualized/dist/commonjs/index.js', {
  paths: [CORE_PACKAGE_ROOT],
});
const TEMPLATE_TOKEN_PATTERN = /\{%[\s\S]*?%\}/;

function runtimeSettingsPlugin(settingsPath: string): Plugin {
  return {
    name: 'deck-runtime-settings',
    apply: 'serve',
    transformIndexHtml: {
      order: 'pre',
      handler(html) {
        return html
          .replace(
            '<script src="./settings.js" vite-ignore></script>',
            `<script type="module" src="/@fs/${normalizePath(settingsPath)}"></script>`,
          )
          .replace(
            '<script src="./settings-local.js" vite-ignore></script>',
            `<script type="module" src="/@fs/${normalizePath(SETTINGS_LOCAL_PATH)}"></script>`,
          );
      },
    },
  };
}

const buildRuntimeOrderPlugin: Plugin = {
  name: 'deck-build-runtime-order',
  apply: 'build',
  transformIndexHtml: {
    order: 'post',
    handler(html) {
      const appModule = html.match(/\s*<script type="module" crossorigin src="\.\/assets\/[^"/]+\.js"><\/script>/);
      if (!appModule) {
        throw new Error('Vite did not emit the Deck app module script');
      }

      return html
        .replace(appModule[0], '')
        .replace(/(<script src="\.\/settings-local\.js"[^>]*><\/script>)/, `$1\n    ${appModule[0].trim()}`);
    },
  },
};

function preserveTemplateSettingsPlugin(settingsPath: string, outputDir: string): Plugin {
  const source = readFileSync(settingsPath, 'utf8');

  return {
    name: 'deck-template-settings',
    apply: 'build',
    generateBundle(_options, bundle) {
      if (!TEMPLATE_TOKEN_PATTERN.test(source)) {
        return;
      }

      const settingsChunk = bundle['settings.js'];
      if (!settingsChunk || settingsChunk.type !== 'chunk') {
        this.error('Vite did not emit the settings.js entry');
      }
      const mapFileName = settingsChunk.sourcemapFileName || 'settings.js.map';
      const settingsMap = bundle[mapFileName];
      if (!settingsMap || settingsMap.type !== 'asset') {
        this.error('Vite did not emit the settings.js source map');
      }

      const sourceMap = {
        version: 3,
        file: settingsChunk.fileName,
        sources: [normalizePath(path.relative(outputDir, settingsPath))],
        sourcesContent: [source],
        names: [],
        mappings: source
          .split('\n')
          .map((_line, index) => (index === 0 ? 'AAAA' : 'AACA'))
          .join(';'),
      };
      settingsChunk.code = `${source.endsWith('\n') ? source : `${source}\n`}//# sourceMappingURL=${path.basename(
        mapFileName,
      )}\n`;
      settingsChunk.map = sourceMap;
      settingsMap.source = JSON.stringify(sourceMap);
    },
  };
}

export default defineConfig(({ command, mode }) => {
  const env = mergeViteEnvironment(loadEnv, mode, APP_ROOT, process.env);
  const settingsPath = resolveSettingsPath(APP_ROOT, env);
  const outputDir = resolveBuildOutput(APP_ROOT, env);
  const tlsMode = getTlsMode(env);
  const plugins: PluginOption[] = [
    react(),
    buildRuntimeOrderPlugin,
    preserveTemplateSettingsPlugin(settingsPath, outputDir),
  ];

  if (tlsMode.kind === 'generated') {
    plugins.push(basicSsl());
  }
  if (command === 'serve') {
    plugins.push(runtimeSettingsPlugin(settingsPath));
  }
  plugins.push(
    viteStaticCopy({
      targets: staticCopyTargets(APP_ROOT, DECK_ROOT, STYLEGUIDE_PACKAGE_ROOT, command),
    }),
  );

  const httpsOptions =
    tlsMode.kind === 'generated'
      ? {}
      : tlsMode.kind === 'custom'
      ? {
          cert: readFileSync(tlsMode.cert),
          key: readFileSync(tlsMode.key),
          ...(tlsMode.ca ? { ca: readFileSync(tlsMode.ca) } : {}),
        }
      : undefined;

  return {
    root: APP_ROOT,
    base: command === 'build' ? './' : '/',
    publicDir: false,
    clearScreen: false,
    envPrefix: [],
    define: buildPublicEnvDefines(env),
    plugins,
    resolve: {
      alias: [
        ...Object.entries(SHARED_PACKAGE_ROOTS).map(([find, replacement]) => ({ find, replacement })),
        { find: '@spinnaker/core', replacement: CORE_PACKAGE_ROOT },
        {
          find: 'coreImports',
          replacement: path.join(CORE_PACKAGE_ROOT, 'src/presentation/less/imports/commonImports.less'),
        },
        { find: 'root', replacement: DECK_ROOT },
        { find: /^react-virtualized$/, replacement: REACT_VIRTUALIZED_COMMONJS },
      ],
      dedupe: SHARED_PACKAGE_NAMES,
      extensions: ['.json', '.ts', '.tsx', '.js', '.jsx', '.css', '.less'],
      mainFields: ['browser', 'module', 'jsnext:main', 'jsnext', 'main:esnext', 'main'],
    },
    build: {
      outDir: outputDir,
      emptyOutDir: true,
      sourcemap: true,
      target: 'es2019',
      assetsInlineLimit: 24000,
      minify: 'terser',
      terserOptions: {
        mangle: false,
        format: {
          comments: /^!|@license|@preserve|@cc_on|webpackIgnore|vite-ignore/i,
        },
      },
      rollupOptions: {
        input: {
          index: path.join(APP_ROOT, 'index.html'),
          settings: settingsPath,
          'settings-local': SETTINGS_LOCAL_PATH,
        },
        output: {
          entryFileNames: ({ name }) =>
            name === 'settings' || name === 'settings-local' ? `${name}.js` : 'assets/[name]-[hash].js',
          chunkFileNames: 'assets/[name]-[hash].js',
          assetFileNames: 'assets/[name]-[hash][extname]',
          manualChunks(id) {
            const normalizedId = normalizePath(id);
            if (normalizedId.startsWith(`${normalizePath(CORE_PACKAGE_ROOT)}/dist/`)) {
              return 'core';
            }
            if (PROVIDER_PACKAGE_ROOTS.some((providerRoot) => normalizedId.startsWith(providerRoot))) {
              return 'providers';
            }
            if (normalizedId.includes('/node_modules/')) {
              return 'vendor';
            }
            return undefined;
          },
        },
      },
    },
    server: {
      host: env.DECK_HOST || 'localhost',
      port: Number(env.DECK_PORT || 9000),
      strictPort: true,
      ...(httpsOptions ? { https: httpsOptions } : {}),
      fs: {
        allow: [DECK_ROOT, settingsPath],
      },
    },
  };
});

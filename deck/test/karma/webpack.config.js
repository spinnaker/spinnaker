'use strict';

const fs = require('fs');
const { createRequire } = require('module');
const path = require('path');

const DECK_ROOT = path.resolve(__dirname, '../..');
const APP_ROOT = path.resolve(DECK_ROOT, 'packages/app');
const MODULES_ROOT = path.resolve(DECK_ROOT, 'packages');
const appRequire = createRequire(`${APP_ROOT}/package.json`);
const cheerioRequire = createRequire(require.resolve('cheerio/package.json'));
const cssSelectRequire = createRequire(cheerioRequire.resolve('css-select/package.json'));
const domhandlerRequire = createRequire(require.resolve('domhandler/package.json'));
const ForkTsCheckerWebpackPlugin = appRequire('fork-ts-checker-webpack-plugin');
const md5 = appRequire('md5');
const { ProvidePlugin } = appRequire('webpack');
const CORE_PACKAGE_ROOT = path.dirname(appRequire.resolve('@spinnaker/core/package.json'));
const REACT_VIRTUALIZED_COMMONJS = require.resolve('react-virtualized/dist/commonjs/index.js', {
  paths: [CORE_PACKAGE_ROOT],
});
const SHARED_PACKAGE_ALIASES = ['@uirouter/core', '@uirouter/react', 'react', 'react-dom'];
const SHARED_PACKAGE_ALIAS_RESOLUTIONS = Object.fromEntries(
  SHARED_PACKAGE_ALIASES.map((packageName) => [
    packageName,
    path.dirname(require.resolve(`${packageName}/package.json`, { paths: [CORE_PACKAGE_ROOT] })),
  ]),
);
const CACHE_INVALIDATE = getCacheInvalidateString();
const THREADS = getThreadLoaderThreads();

module.exports = {
  mode: 'development',
  resolveLoader: {
    modules: [path.resolve(APP_ROOT, 'node_modules'), path.resolve(DECK_ROOT, 'node_modules')],
  },
  module: {
    rules: [
      {
        test: /settings\.js/,
        use: [{ loader: 'envify-loader' }, { loader: path.resolve(__dirname, 'webpackImportMetaLoader.js') }],
      },
      {
        test: /\.js$/,
        use: [
          { loader: 'cache-loader', options: { cacheIdentifier: CACHE_INVALIDATE } },
          { loader: 'thread-loader', options: { workers: THREADS } },
          { loader: 'babel-loader' },
        ],
        exclude: /(node_modules(?!\/clipboard)|settings\.js|packages\/[^/]+\/dist\/)/,
      },
      {
        test: /\.tsx?$/,
        use: [
          { loader: 'cache-loader', options: { cacheIdentifier: CACHE_INVALIDATE } },
          { loader: 'thread-loader', options: { workers: THREADS } },
          { loader: 'ts-loader', options: { happyPackMode: true } },
        ],
        exclude: /node_modules/,
      },
      {
        test: /\.less$/,
        use: [
          { loader: 'style-loader' },
          { loader: 'css-loader' },
          { loader: 'postcss-loader' },
          { loader: 'less-loader' },
        ],
      },
      {
        test: /\.css$/,
        use: [{ loader: 'style-loader' }, { loader: 'css-loader' }, { loader: 'postcss-loader' }],
      },
      {
        test: /\.(woff|woff2|otf|ttf|eot|png|gif|ico|svg)$/,
        use: [{ loader: 'file-loader', options: { name: '[name].[hash:5].[ext]' } }],
      },
      {
        test: appRequire.resolve('jquery'),
        use: [{ loader: 'expose-loader?$' }, { loader: 'expose-loader?jQuery' }],
      },
      {
        test: /\.js$/,
        enforce: 'pre',
        use: [
          {
            loader: 'source-map-loader',
            options: {
              filterSourceMappingUrl: (url, resourcePath) => {
                // Skip source maps that cause issues with thread-loader.
                if (/.*\/node_modules\/(rxjs-compat|graphql-tag)\/.*/.test(resourcePath)) {
                  return false;
                }
                // Skip pre-bundled @spinnaker package source maps that cause thread-loader issues.
                if (/\/packages\/[^/]+\/dist\//.test(resourcePath)) {
                  return false;
                }
                return true;
              },
            },
          },
        ],
      },
    ],
  },
  resolve: {
    extensions: ['.json', '.ts', '.tsx', '.js', '.jsx', '.css', '.less'],
    modules: [
      path.resolve(APP_ROOT, 'node_modules'),
      path.resolve(MODULES_ROOT, 'core/node_modules'),
      path.resolve(DECK_ROOT, 'node_modules'),
      'node_modules',
    ],
    // Webpack 5 no longer auto-polyfills Node.js core modules for browser builds.
    // Some test dependencies (e.g., parse5, util) require these modules.
    fallback: {
      stream: require.resolve('stream-browserify'),
      buffer: require.resolve('buffer/'),
      util: require.resolve('util/'),
    },
    alias: {
      ...SHARED_PACKAGE_ALIAS_RESOLUTIONS,
      root: DECK_ROOT,
      'react-virtualized$': REACT_VIRTUALIZED_COMMONJS,
      // ts-invariant imports 'process/browser' without extension, which fails in webpack 5 ESM resolution
      'process/browser': require.resolve('process/browser.js'),
      'css-select': cheerioRequire.resolve('css-select'),
      'css-what': cssSelectRequire.resolve('css-what'),
      domelementtype: domhandlerRequire.resolve('domelementtype'),
      coreImports: path.resolve(MODULES_ROOT, 'core/src/presentation/less/imports/commonImports.less'),
      amazon: path.resolve(MODULES_ROOT, 'amazon/src'),
      '@spinnaker/amazon': path.resolve(MODULES_ROOT, 'amazon/src'),
      appengine: path.resolve(MODULES_ROOT, 'appengine/src'),
      '@spinnaker/appengine': path.resolve(MODULES_ROOT, 'appengine/src'),
      azure: path.resolve(MODULES_ROOT, 'azure/src'),
      '@spinnaker/azure': path.resolve(MODULES_ROOT, 'azure/src'),
      cloudfoundry: path.resolve(MODULES_ROOT, 'cloudfoundry/src'),
      '@spinnaker/cloudfoundry': path.resolve(MODULES_ROOT, 'cloudfoundry/src'),
      cloudrun: path.resolve(MODULES_ROOT, 'cloudrun/src'),
      '@spinnaker/cloudrun': path.resolve(MODULES_ROOT, 'cloudrun/src'),
      core: path.resolve(MODULES_ROOT, 'core/src'),
      '@spinnaker/core': path.resolve(MODULES_ROOT, 'core/src'),
      dcos: path.resolve(MODULES_ROOT, 'dcos/src'),
      '@spinnaker/dcos': path.resolve(MODULES_ROOT, 'dcos/src'),
      docker: path.resolve(MODULES_ROOT, 'docker/src'),
      '@spinnaker/docker': path.resolve(MODULES_ROOT, 'docker/src'),
      ecs: path.resolve(MODULES_ROOT, 'ecs/src'),
      '@spinnaker/ecs': path.resolve(MODULES_ROOT, 'ecs/src'),
      google: path.resolve(MODULES_ROOT, 'google/src'),
      '@spinnaker/google': path.resolve(MODULES_ROOT, 'google/src'),
      huaweicloud: path.resolve(MODULES_ROOT, 'huaweicloud/src'),
      '@spinnaker/huaweicloud': path.resolve(MODULES_ROOT, 'huaweicloud/src'),
      kubernetes: path.resolve(MODULES_ROOT, 'kubernetes/src'),
      '@spinnaker/kubernetes': path.resolve(MODULES_ROOT, 'kubernetes/src'),
      mocks: path.resolve(MODULES_ROOT, 'mocks/src'),
      '@spinnaker/mocks': path.resolve(MODULES_ROOT, 'mocks/src'),
      oracle: path.resolve(MODULES_ROOT, 'oracle/src'),
      '@spinnaker/oracle': path.resolve(MODULES_ROOT, 'oracle/src'),
      tencentcloud: path.resolve(MODULES_ROOT, 'tencentcloud/src'),
      '@spinnaker/tencentcloud': path.resolve(MODULES_ROOT, 'tencentcloud/src'),
    },
  },
  plugins: [
    new ForkTsCheckerWebpackPlugin({
      typescript: {
        configFile: path.resolve(MODULES_ROOT, 'tsconfig.app.base.json'),
        configOverwrite: {
          include: ['*/src/**/*.ts', '*/src/**/*.tsx'],
          exclude: ['**/*.stories.*'],
        },
        context: MODULES_ROOT,
        diagnosticOptions: {
          syntactic: true,
          semantic: false,
        },
      },
    }),
    // Webpack 5 no longer auto-polyfills Node.js globals like 'process' and 'Buffer'.
    // Some dependencies (e.g., util, parse5) expect these to exist in browser context.
    // Note: setImmediate is polyfilled via import in karma-shim.js
    new ProvidePlugin({
      process: 'process/browser.js',
      Buffer: ['buffer', 'Buffer'],
    }),
  ],
};

// Invalidate the cache-loader cache when these change.
function getCacheInvalidateString() {
  return JSON.stringify({
    PNPM_LOCK: md5(fs.readFileSync(path.resolve(DECK_ROOT, 'pnpm-lock.yaml'))),
    WEBPACK_CONFIG: md5(fs.readFileSync(__filename)),
  });
}

function getThreadLoaderThreads() {
  const cpus = require('os').cpus().length;
  const physicalCpus = appRequire('physical-cpu-count');
  const threads = process.env.THREADS || (physicalCpus > 3 ? 2 : 1);

  // eslint-disable-next-line no-console
  console.log(`INFO: cpus: ${cpus} physical: ${physicalCpus} thread-loader threads: ${threads}`);

  return threads;
}

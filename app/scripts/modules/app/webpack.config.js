const fs = require('fs');
const path = require('path');
const md5 = require('md5');

const HtmlWebpackPlugin = require('html-webpack-plugin');
const ForkTsCheckerWebpackPlugin = require('fork-ts-checker-webpack-plugin');
const CopyWebpackPlugin = require('copy-webpack-plugin');
const TerserPlugin = require('terser-webpack-plugin');
const ESLintPlugin = require('eslint-webpack-plugin');
const webpack = require('webpack');

const CACHE_INVALIDATE = getCacheInvalidateString();
const SETTINGS_PATH = process.env.SETTINGS_PATH || './src/settings.js';
const THREADS = getThreadLoaderThreads();
// Used to fail CI for PRs which contain linter errors
const ESLINT_FAIL_ON_ERROR = process.env.ESLINT_FAIL_ON_ERROR === 'true';
const DECK_ROOT = path.resolve(`${__dirname}/../../../../`);
const NODE_MODULE_PATH = path.resolve(`${DECK_ROOT}/node_modules`);

function configure(env, webpackOpts) {
  const WEBPACK_MODE = (webpackOpts && webpackOpts.mode) || 'development';
  const IS_PRODUCTION = WEBPACK_MODE === 'production';
  const IS_CI = !!process.env.TRAVIS || !!process.env.GITHUB_ACTIONS;
  const DISPLAY_PROGRESS = process.stdout.isTTY && !IS_CI;

  // eslint-disable-next-line no-console
  console.log('Webpack mode: ' + WEBPACK_MODE);

  const plugins = [
    new ESLintPlugin({ failOnError: ESLINT_FAIL_ON_ERROR, threads: 4 }),
    new ForkTsCheckerWebpackPlugin({ checkSyntacticErrors: true }),
    new CopyWebpackPlugin([
      { from: `${NODE_MODULE_PATH}/@spinnaker/styleguide/public/styleguide.html`, to: `./styleguide.html` },
      { from: `./src/plugin-manifest.json`, to: `./plugin-manifest.json` },
    ]),
    new HtmlWebpackPlugin({
      title: 'Spinnaker',
      template: 'index.deck',
      favicon: process.env.NODE_ENV === 'production' ? 'icons/prod-favicon.ico' : 'icons/dev-favicon.ico',
      inject: true,
      hash: IS_PRODUCTION,
    }),
  ];

  if (process.env.NODE_ENV !== 'production') {
    plugins.push(new webpack.HotModuleReplacementPlugin());
  }

  if (DISPLAY_PROGRESS) {
    plugins.push(new webpack.ProgressPlugin({ profile: false }));
  }

  const config = {
    context: __dirname,
    mode: WEBPACK_MODE,
    stats: 'errors-only',
    watch: process.env.WATCH === 'true',
    entry: {
      settings: SETTINGS_PATH,
      'settings-local': './src/settings-local.js',
      app: './src/app.ts',
    },
    output: {
      path: path.join(`${__dirname}/../../../../build/webpack`, process.env.SPINNAKER_ENV || ''),
      filename: '[name].js',
    },
    devtool: IS_PRODUCTION ? 'source-map' : 'eval',
    optimization: {
      splitChunks: {
        chunks: 'all', // enables splitting of both initial and async chunks
        maxInitialRequests: 20, // allows up to 10 initial chunks
        cacheGroups: {
          // Put code matching each regexp in a separate chunk
          core: new RegExp('node_modules/@spinnaker/core/'),
          providers: new RegExp('node_modules/@spinnaker/(?!core)[^/]+/'),
          vendor_A_F: new RegExp('node_modules/[a-fA-F]'),
          vendor_G_O: new RegExp('node_modules/[g-oG-O]'),
          vendor_P_Q: new RegExp('node_modules/[^a-oA-Or-zR-Z]'),
          vendor_R_Z: new RegExp('node_modules/[r-zR-Z]'),
        },
      },
      minimizer: IS_PRODUCTION
        ? [
            new TerserPlugin({
              cache: true,
              parallel: true,
              sourceMap: true,
              terserOptions: {
                ecma: 2017,
                mangle: false,
                output: {
                  comments: /webpackIgnore/,
                },
              },
            }),
          ]
        : [], // Disable minification unless production
    },
    resolve: {
      extensions: ['.json', '.ts', '.tsx', '.js', '.jsx', '.css', '.less', '.html'],
      alias: {
        coreImports: path.resolve(
          NODE_MODULE_PATH,
          '@spinnaker',
          'core',
          'src',
          'presentation',
          'less',
          'imports',
          'commonImports.less',
        ),
        root: DECK_ROOT,
      },
    },
    module: {
      rules: [
        {
          test: /settings\.js/,
          use: [{ loader: 'envify-loader' }],
        },
        {
          test: /\.js$/,
          use: [
            { loader: 'cache-loader', options: { cacheIdentifier: CACHE_INVALIDATE } },
            { loader: 'thread-loader', options: { workers: THREADS } },
            { loader: 'babel-loader' },
          ],
          exclude: /(node_modules(?!\/clipboard)|settings\.js)/,
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
          test: /\.html$/,
          use: [{ loader: 'ngtemplate-loader?relativeTo=' + path.resolve(__dirname) + '/' }, { loader: 'html-loader' }],
        },
        {
          test: /\.(woff|woff2|otf|ttf|eot|png|gif|ico|svg)$/,
          use: [{ loader: 'file-loader', options: { name: '[name].[hash:5].[ext]' } }],
        },
        {
          test: require.resolve('jquery'),
          use: [{ loader: 'expose-loader?$' }, { loader: 'expose-loader?jQuery' }],
        },
        {
          test: /ui-sortable/,
          use: ['imports-loader?$UI=jquery-ui/ui/widgets/sortable'],
        },
        {
          test: /\.js$/,
          enforce: 'pre',
          use: [
            {
              loader: 'source-map-loader',
              options: {
                filterSourceMappingUrl: (url, resourcePath) => {
                  if (IS_PRODUCTION) return true;
                  if (/.*\/node_modules\/(rxjs-compat|graphql-tag)\/.*/.test(resourcePath)) {
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
    plugins,
    devServer: {
      hot: true,
      disableHostCheck: true,
      port: process.env.DECK_PORT || 9000,
      host: process.env.DECK_HOST || 'localhost',
      https: process.env.DECK_HTTPS === 'true',
      stats: 'errors-only',
    },
    externals: {
      'react/addons': 'react',
      'react/lib/ExecutionEnvironment': 'react',
      'react/lib/ReactContext': 'react',
    },
  };

  if (process.env.DECK_CERT) {
    config.devServer.cert = fs.readFileSync(process.env.DECK_CERT);
    config.devServer.key = fs.readFileSync(process.env.DECK_KEY);
    if (process.env.DECK_CA_CERT) {
      config.devServer.ca = fs.readFileSync(process.env.DECK_CA_CERT);
    }
  }

  return config;
}

// invalidate cache-loader cache when these change
function getCacheInvalidateString() {
  return JSON.stringify({
    YARN_LOCK: md5(fs.readFileSync('yarn.lock')),
    WEBPACK_CONFIG: md5(fs.readFileSync(__filename)),
  });
}

function getThreadLoaderThreads() {
  const cpus = require('os').cpus().length;
  const physicalCpus = require('physical-cpu-count');
  const threads = process.env.THREADS || (physicalCpus > 3 ? 2 : 1);

  // eslint-disable-next-line no-console
  console.log(`INFO: cpus: ${cpus} physical: ${physicalCpus} thread-loader threads: ${threads}`);

  return threads;
}

module.exports = configure;

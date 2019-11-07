const fs = require('fs');
const path = require('path');
const md5 = require('md5');

const HtmlWebpackPlugin = require('html-webpack-plugin');
const ForkTsCheckerWebpackPlugin = require('fork-ts-checker-webpack-plugin');
const CopyWebpackPlugin = require('copy-webpack-plugin');
const TerserPlugin = require('terser-webpack-plugin');

const CACHE_INVALIDATE = getCacheInvalidateString();
const NODE_MODULE_PATH = path.join(__dirname, 'node_modules');
const SETTINGS_PATH = process.env.SETTINGS_PATH || './settings.js';
const THREADS = getThreadLoaderThreads();

function configure(env, webpackOpts) {
  const WEBPACK_MODE = (webpackOpts && webpackOpts.mode) || 'development';
  const IS_PRODUCTION = WEBPACK_MODE === 'production';

  console.log('Webpack mode: ' + WEBPACK_MODE);

  const config = {
    context: __dirname,
    mode: WEBPACK_MODE,
    stats: 'errors-only',
    watch: process.env.WATCH === 'true',
    entry: {
      settings: SETTINGS_PATH,
      'settings-local': './settings-local.js',
      app: './app/scripts/app.ts',
    },
    output: {
      path: path.join(__dirname, 'build', 'webpack', process.env.SPINNAKER_ENV || ''),
      filename: '[name].js',
    },
    devtool: IS_PRODUCTION ? 'source-map' : 'eval',
    optimization: {
      splitChunks: {
        chunks: 'all', // enables splitting of both initial and async chunks
        maxInitialRequests: 20, // allows up to 10 initial chunks
        cacheGroups: {
          // Put code matching each regexp in a separate chunk
          core: new RegExp('/app/scripts/modules/core/'),
          providers: new RegExp('/app/scripts/modules/(?!core)[^/]+/'),
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
                ecma: 6,
                mangle: false,
                output: {
                  comments: false,
                },
              },
            }),
          ]
        : [], // Disable minification unless production
    },
    resolve: {
      extensions: ['.json', '.ts', '.tsx', '.js', '.jsx', '.css', '.less', '.html'],
      modules: [NODE_MODULE_PATH, path.join(__dirname, 'app', 'scripts', 'modules')],
      alias: {
        root: __dirname,
        core: path.join(__dirname, 'app', 'scripts', 'modules', 'core', 'src'),
        '@spinnaker/core': path.join(__dirname, 'app', 'scripts', 'modules', 'core', 'src'),
        docker: path.join(__dirname, 'app', 'scripts', 'modules', 'docker', 'src'),
        '@spinnaker/docker': path.join(__dirname, 'app', 'scripts', 'modules', 'docker', 'src'),
        amazon: path.join(__dirname, 'app', 'scripts', 'modules', 'amazon', 'src'),
        '@spinnaker/amazon': path.join(__dirname, 'app', 'scripts', 'modules', 'amazon', 'src'),
        google: path.join(__dirname, 'app', 'scripts', 'modules', 'google', 'src'),
        '@spinnaker/google': path.join(__dirname, 'app', 'scripts', 'modules', 'google', 'src'),
        kubernetes: path.join(__dirname, 'app', 'scripts', 'modules', 'kubernetes', 'src'),
        '@spinnaker/kubernetes': path.join(__dirname, 'app', 'scripts', 'modules', 'kubernetes', 'src'),
        ecs: path.join(__dirname, 'app', 'scripts', 'modules', 'ecs', 'src'),
        '@spinnaker/ecs': path.join(__dirname, 'app', 'scripts', 'modules', 'ecs', 'src'),
        huaweicloud: path.join(__dirname, 'app', 'scripts', 'modules', 'huaweicloud', 'src'),
        '@spinnaker/huaweicloud': path.join(__dirname, 'app', 'scripts', 'modules', 'huaweicloud', 'src'),
        coreImports: path.resolve(
          __dirname,
          'app',
          'scripts',
          'modules',
          'core',
          'src',
          'presentation',
          'less',
          'imports',
          'commonImports.less',
        ),
        appengine: path.join(__dirname, 'app', 'scripts', 'modules', 'appengine', 'src'),
        '@spinnaker/appengine': path.join(__dirname, 'app', 'scripts', 'modules', 'appengine', 'src'),
        oracle: path.join(__dirname, 'app', 'scripts', 'modules', 'oracle', 'src'),
        '@spinnaker/oracle': path.join(__dirname, 'app', 'scripts', 'modules', 'oracle', 'src'),
        cloudfoundry: path.join(__dirname, 'app', 'scripts', 'modules', 'cloudfoundry', 'src'),
        '@spinnaker/cloudfoundry': path.join(__dirname, 'app', 'scripts', 'modules', 'cloudfoundry', 'src'),
        titus: path.join(__dirname, 'app', 'scripts', 'modules', 'titus', 'src'),
        '@spinnaker/titus': path.join(__dirname, 'app', 'scripts', 'modules', 'titus', 'src'),
        azure: path.join(__dirname, 'app', 'scripts', 'modules', 'azure', 'src'),
        '@spinnaker/azure': path.join(__dirname, 'app', 'scripts', 'modules', 'azure', 'src'),
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
            { loader: 'eslint-loader' },
          ],
          exclude: /(node_modules(?!\/clipboard)|settings\.js)/,
        },
        {
          test: /\.tsx?$/,
          use: [
            { loader: 'cache-loader', options: { cacheIdentifier: CACHE_INVALIDATE } },
            { loader: 'thread-loader', options: { workers: THREADS } },
            { loader: 'ts-loader', options: { happyPackMode: true } },
            { loader: 'tslint-loader' },
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
      ],
    },
    plugins: [
      new ForkTsCheckerWebpackPlugin({ checkSyntacticErrors: true, tslint: true }),
      new CopyWebpackPlugin([
        {
          from: `${NODE_MODULE_PATH}/@spinnaker/styleguide/public/styleguide.html`,
          to: `./styleguide.html`,
        },
      ]),
      new HtmlWebpackPlugin({
        title: 'Spinnaker',
        template: './app/index.deck',
        favicon: process.env.NODE_ENV === 'production' ? 'app/prod-favicon.ico' : 'app/dev-favicon.ico',
        inject: true,
        hash: IS_PRODUCTION,
      }),
    ],
    devServer: {
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

// invalidate webpack cache when webpack config is changed or cache-loader is updated,
function getCacheInvalidateString() {
  return JSON.stringify({
    CACHE_LOADER: require('cache-loader/package.json').version,
    WEBPACK_CONFIG: md5(fs.readFileSync(__filename)),
  });
}

// When running locally, use one less than the physical number of cpus
// When running on travis, use max of 2 threads
// https://docs.travis-ci.com/user/reference/overview/#Virtualization-environments
function getThreadLoaderThreads() {
  const bareMetalThreads = Math.max(require('physical-cpu-count') - 1, 1);
  const travisThreads = Math.min(require('physical-cpu-count'), 2);
  const autoThreads = !!process.env.TRAVIS ? travisThreads : bareMetalThreads;
  const threads = process.env.THREADS || autoThreads;

  console.log(
    `INFO: cpus: ${
      require('os').cpus().length
    } physical: ${require('physical-cpu-count')} thread-loader threads: ${threads}`,
  );

  return threads;
}

module.exports = configure;

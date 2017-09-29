const webpack = require('webpack');
const HappyPack = require('happypack');
const HAPPY_PACK_POOL_SIZE = process.env.HAPPY_PACK_POOL_SIZE || 3;
const happyThreadPool = HappyPack.ThreadPool({size: HAPPY_PACK_POOL_SIZE});
const ForkTsCheckerWebpackPlugin = require('fork-ts-checker-webpack-plugin');
const lodash = require('lodash');
const HAPPY_PACK_ENV_INVALIDATE = lodash.pick(process.env, [
  'FEEDBACK_URL',
  'API_HOST',
  'BAKERY_DETAIL_URL',
  'AUTH_ENDPOINT',
  'AUTH_ENABLED',
  'NETFLIX_MODE',
  'CHAOS_ENABLED',
  'FIAT_ENABLED',
  'ENTITY_TAGS_ENABLED',
  'DEBUG_ENABLED',
  'CANARY_ENABLED',
  'INF_SEARCH_ENABLED',
]);
const HtmlWebpackPlugin = require('html-webpack-plugin');

const path = require('path');
const NODE_MODULE_PATH = path.join(__dirname, 'node_modules');
const fs = require('fs');
function configure(IS_TEST) {

  const config = {
    context: __dirname, // to automatically find tsconfig.json,
    plugins: [],
    output: IS_TEST ? undefined : {
        path: path.join(__dirname, 'build', 'webpack', process.env.SPINNAKER_ENV || ''),
        filename: '[name].js',
      },
    resolveLoader: IS_TEST ? {} : {
        modules: [
          NODE_MODULE_PATH
        ],
        moduleExtensions: ['-loader']
      },
    resolve: {
      extensions: ['.json', '.ts', '.tsx', '.js', '.jsx', '.css', '.less', '.html'],
      modules: [
        NODE_MODULE_PATH,
        path.join(__dirname, 'app', 'scripts', 'modules'),
      ],
      alias: {
        'root': __dirname,
        'core': path.join(__dirname, 'app', 'scripts', 'modules', 'core', 'src'),
        '@spinnaker/core': path.join(__dirname, 'app', 'scripts', 'modules', 'core', 'src'),
        'docker': path.join(__dirname, 'app', 'scripts', 'modules', 'docker', 'src'),
        '@spinnaker/docker': path.join(__dirname, 'app', 'scripts', 'modules', 'docker', 'src'),
        'amazon': path.join(__dirname, 'app', 'scripts', 'modules', 'amazon', 'src'),
        '@spinnaker/amazon': path.join(__dirname, 'app', 'scripts', 'modules', 'amazon', 'src'),
        'google': path.join(__dirname, 'app', 'scripts', 'modules', 'google', 'src'),
        '@spinnaker/google': path.join(__dirname, 'app', 'scripts', 'modules', 'google', 'src'),
        'coreImports': path.resolve(__dirname, 'app', 'scripts', 'modules', 'core', 'src', 'presentation', 'less', 'imports', 'commonImports.less'),
        'coreColors': path.resolve(__dirname, 'app', 'scripts', 'modules', 'core', 'src', 'presentation', 'less', 'imports', 'colors.less'),
      }
    },
    devtool: 'source-map',
    module: {
      rules: [
        {test: /\.js$/, use: ['happypack/loader?id=js'], exclude: /node_modules(?!\/clipboard)/},
        {test: /\.tsx?$/, use: ['happypack/loader?id=ts'], exclude: /node_modules/},
        {test: /\.json$/, loader: 'json-loader'},
        {
          test: /\.(woff|woff2|otf|ttf|eot|png|gif|ico|svg)$/,
          loader: 'file-loader',
          options: { name: '[name].[hash:5].[ext]'}
        },
        {
          test: require.resolve('jquery'),
          use: [
            'expose-loader?$',
            'expose-loader?jQuery'
          ]
        },
        {
          test: /\.less$/,
          use: IS_TEST ? ['style-loader', 'css-loader', 'postcss-loader', 'less-loader'] : ['happypack/loader?id=less']
        },
        {
          test: /\.css$/,
          use: [
            'style-loader',
            'css-loader',
            'postcss-loader'
          ]
        },
        {
          test: /\.html$/,
          use: ['happypack/loader?id=html']
        }
      ],
    },
    devServer: IS_TEST ? {} : {
        port: process.env.DECK_PORT || 9000,
        host: process.env.DECK_HOST || 'localhost',
        https: process.env.DECK_HTTPS === 'true'
      },
    watch: IS_TEST,
    externals: {
      'cheerio': 'window',
      'react/addons': 'react',
      'react/lib/ExecutionEnvironment': 'react',
      'react/lib/ReactContext': 'react',
    },
  };

  if (process.env.DECK_CERT) {
    config.devServer.cert = fs.readFileSync(process.env.DECK_CERT);
    config.devServer.key = fs.readFileSync(process.env.DECK_KEY);
    if (process.env.DECK_CA_CERT) {
      config.devServer.cacert = fs.readFileSync(process.env.DECK_CA_CERT);
    }
  }

  config.plugins = [
    new ForkTsCheckerWebpackPlugin({ checkSyntacticErrors: true }),
    new HappyPack({
      id: 'html',
      loaders: [
        'ngtemplate-loader?relativeTo=' + (path.resolve(__dirname)) + '/',
        'html-loader'
      ],
      threadPool: happyThreadPool
    }),
    new HappyPack({
      id: 'js',
      loaders: [
        'babel-loader',
        'envify-loader',
        'eslint-loader'
      ],
      threadPool: happyThreadPool,
      cacheContext: {
        env: HAPPY_PACK_ENV_INVALIDATE
      }
    }),
    new HappyPack({
      id: 'ts',
      loaders: [
        'babel-loader',
        { path: 'ts-loader', query: { happyPackMode: true } },
        'tslint-loader',
      ],
      threadPool: happyThreadPool,
      cacheContext: {
        env: HAPPY_PACK_ENV_INVALIDATE
      }
    }),
  ];

  if (!IS_TEST) {
    config.entry = {
      settings: './settings.js',
      settingsLocal: './settings-local.js',
      app: './app/scripts/app.ts',
      vendor: [
        'jquery', 'angular', 'angular-ui-bootstrap', 'source-sans-pro',
        'angular-cache', 'angular-messages', 'angular-sanitize', 'bootstrap',
        'clipboard', 'd3', 'jquery-ui', 'moment-timezone', 'rxjs', 'react', 'angular2react',
        'react2angular', 'react-bootstrap', 'react-dom', 'react-ga', 'ui-select',
        '@uirouter/angularjs', '@uirouter/visualizer',
      ]
    };

    config.plugins.push(...[
      new HappyPack({
        id: 'less',
        loaders: [
          'style-loader',
          'css-loader',
          'postcss-loader',
          'less-loader'
        ],
        threadPool: happyThreadPool
      }),
      new webpack.optimize.CommonsChunkPlugin({name: 'vendor', filename: 'vendor.bundle.js'}),
      new webpack.optimize.CommonsChunkPlugin('init'),
      new HtmlWebpackPlugin({
        title: 'Spinnaker',
        template: './app/index.deck',
        favicon: 'app/favicon.ico',
        inject: true,

        // default order is based on webpack's compile process
        // with the migration to webpack two, we need this or
        // settings.js is put at the end of the <script> blocks
        // which breaks the booting of the app.
        chunksSortMode: (a, b) => {
          const chunks = ['init', 'vendor', 'settings', 'settingsLocal', 'app'];
          return chunks.indexOf(a.names[0]) - chunks.indexOf(b.names[0]);
        }
      })
    ]);
  }

  // this is temporary and will be deprecated in WP3.  moving forward,
  // loaders will individually need to accept this as an option.
  config.plugins.push(new webpack.LoaderOptionsPlugin({debug: !IS_TEST}));

  return config;
}

module.exports = configure;

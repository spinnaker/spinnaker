const webpack = require('webpack');
const fs = require('fs');
const path = require('path');
const md5 = require('md5');

const HtmlWebpackPlugin = require('html-webpack-plugin');
const ForkTsCheckerWebpackPlugin = require('fork-ts-checker-webpack-plugin');
const CopyWebpackPlugin = require('copy-webpack-plugin');

// invalidate webpack cache when webpack config is changed or cache-loader is updated,
const CACHE_INVALIDATE = JSON.stringify({
  THREAD_LOADER:       require('cache-loader/package.json').version,
  WEBPACK_CONFIG:      md5(fs.readFileSync(__filename)),
});

const NODE_MODULE_PATH = path.join(__dirname, 'node_modules');
const SETTINGS_PATH = process.env.SETTINGS_PATH || './settings.js';

function configure(IS_TEST) {

  const config = {
    context: __dirname, // to automatically find tsconfig.json,
    stats: 'errors-only',
    devtool: 'source-map',
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
        'kubernetes': path.join(__dirname, 'app', 'scripts', 'modules', 'kubernetes', 'src'),
        '@spinnaker/kubernetes': path.join(__dirname, 'app', 'scripts', 'modules', 'kubernetes', 'src'),
        'openstack': path.join(__dirname, 'app', 'scripts', 'modules', 'openstack', 'src'),
        '@spinnaker/openstack': path.join(__dirname, 'app', 'scripts', 'modules', 'openstack', 'src'),
        'coreImports': path.resolve(__dirname, 'app', 'scripts', 'modules', 'core', 'src', 'presentation', 'less', 'imports', 'commonImports.less'),
      }
    },
    module: {
      rules: [
        {
          test: /settings\.js/,
          use: [
            { loader: 'envify-loader' },
          ],
        },
        {
          test: /\.js$/,
          use: [
            { loader: 'cache-loader', options: { cacheIdentifier: CACHE_INVALIDATE } },
            { loader: 'thread-loader', options: { workers: 3 } },
            { loader: 'babel-loader' },
            { loader: 'eslint-loader' } ,
          ],
          exclude: /(node_modules(?!\/clipboard)|settings\.js)/
        },
        {
          test: /\.tsx?$/,
          use: [
            { loader: 'cache-loader', options: { cacheIdentifier: CACHE_INVALIDATE } },
            { loader: 'thread-loader', options: { workers: 3 } },
            { loader: 'babel-loader' },
            { loader: 'ts-loader', options: { happyPackMode: true } },
            { loader: 'tslint-loader' },
          ],
          exclude: /node_modules/
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
          use: [
            { loader: 'style-loader' },
            { loader: 'css-loader' },
            { loader: 'postcss-loader' },
          ]
        },
        {
          test: /\.html$/,
          use: [
            { loader: 'ngtemplate-loader?relativeTo=' + (path.resolve(__dirname)) + '/' },
            { loader: 'html-loader' },
          ]
        },
        {
          test: /\.json$/,
          use: [
            { loader: 'json-loader' },
          ],
        },
        {
          test: /\.(woff|woff2|otf|ttf|eot|png|gif|ico|svg)$/,
          use: [
            { loader: 'file-loader', options: { name: '[name].[hash:5].[ext]'} },
          ],
        },
        {
          test: require.resolve('jquery'),
          use: [
            { loader: 'expose-loader?$' },
            { loader: 'expose-loader?jQuery' },
          ],
        },
        {
          test: /ui-sortable/,
          use: ['imports-loader?$UI=jquery-ui/ui/widgets/sortable']
        }
      ],
    },
    watch: IS_TEST,
    devServer: IS_TEST ? {
      stats: 'errors-only',
    } : {
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
      config.devServer.cacert = fs.readFileSync(process.env.DECK_CA_CERT);
    }
  }

  config.plugins = [
    new ForkTsCheckerWebpackPlugin({ checkSyntacticErrors: true, tslint: true }),
    new CopyWebpackPlugin([
      {
        from: `${NODE_MODULE_PATH}/@spinnaker/styleguide/public/styleguide.html`,
        to: `./styleguide.html`,
      }
    ]),
  ];

  if (!IS_TEST) {
    config.entry = {
      settings: SETTINGS_PATH,
      'settings-local': './settings-local.js',
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
      new webpack.EnvironmentPlugin({
        NODE_ENV: 'development',
      }),
      new webpack.optimize.CommonsChunkPlugin({name: 'vendor', filename: 'vendor.bundle.js'}),
      new webpack.optimize.CommonsChunkPlugin('init'),
      new HtmlWebpackPlugin({
        title: 'Spinnaker',
        template: './app/index.deck',
        favicon: process.env.NODE_ENV === 'production' ? 'app/prod-favicon.ico' : 'app/dev-favicon.ico',
        inject: true,

        // default order is based on webpack's compile process
        // with the migration to webpack two, we need this or
        // settings.js is put at the end of the <script> blocks
        // which breaks the booting of the app.
        chunksSortMode: (a, b) => {
          const chunks = ['init', 'vendor', 'settings', 'settings-local', 'app'];
          return chunks.indexOf(a.names[0]) - chunks.indexOf(b.names[0]);
        }
      })
    ]);

    if (process.env.NODE_ENV === 'production') {
      config.plugins.push(new webpack.optimize.ModuleConcatenationPlugin());
      config.plugins.push(new webpack.optimize.UglifyJsPlugin({ include: /vendor/, sourceMap: true }));
    }
  }

  // this is temporary and will be deprecated in WP3.  moving forward,
  // loaders will individually need to accept this as an option.
  config.plugins.push(new webpack.LoaderOptionsPlugin({debug: !IS_TEST}));

  return config;
}

module.exports = configure;

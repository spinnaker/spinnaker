const HtmlWebpackPlugin = require('html-webpack-plugin');
const {CheckerPlugin} = require('awesome-typescript-loader');
const webpack = require('webpack');
const path = require('path');
const NODE_MODULE_PATH = path.join(__dirname, 'node_modules');
const fs = require('fs');

function configure(IS_TEST) {

  const config = {
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
      extensions: ['.json', '.js', '.ts', '.css', '.less', '.html'],
      modules: [
        NODE_MODULE_PATH,
        path.join(__dirname, 'app', 'scripts', 'modules'),
      ]
    },
    module: {
      rules: [
        {enforce: 'pre', test: /\.(spec\.)?ts$/, use: 'tslint-loader'},
        {enforce: 'pre', test: /\.(spec\.)?js$/, loader: 'eslint-loader'},
        {test: /\.json$/, loader: 'json-loader'},
        {test: /\.ts$/, use: 'awesome-typescript-loader', exclude: /node_modules/},
        {test: /\.(woff|otf|ttf|eot|svg|png|gif|ico)(.*)?$/, use: 'file-loader'},
        {
          test: /\.js$/,
          exclude: /node_modules(?!\/clipboard)/,
          use: [
            'ng-annotate-loader',
            'angular-loader',
            'babel-loader',
            'envify-loader',
            'eslint-loader'
          ]
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
          use: [
            'style-loader',
            'css-loader',
            'less-loader'
          ]
        },
        {
          test: /\.css$/,
          use: [
            'style-loader',
            'css-loader'
          ]
        },
        {
          test: /\.html$/,
          use: [
            'ngtemplate-loader?relativeTo=' + (path.resolve(__dirname)) + '/',
            'html-loader'
          ]
        }
      ],
    },
    devServer: IS_TEST ? {} : {
        port: process.env.DECK_PORT || 9000,
        host: process.env.DECK_HOST || 'localhost',
        https: process.env.DECK_HTTPS === 'true'
      },
    watch: IS_TEST
  };

  if (process.env.DECK_CERT) {
    config.devServer.cert = fs.readFileSync(process.env.DECK_CERT);
    config.devServer.key = fs.readFileSync(process.env.DECK_KEY);
    if (process.env.DECK_CA_CERT) {
      config.devServer.cacert = fs.readFileSync(process.env.DECK_CA_CERT);
    }
  }

  if (!IS_TEST) {
    config.entry = {
      settings: './settings.js',
      app: './app/scripts/app.js',
      vendor: [
        'jquery', 'angular', 'angular-ui-bootstrap', 'angular-ui-router', 'source-sans-pro',
        'angular-cache', 'angular-marked', 'angular-messages', 'angular-sanitize', 'bootstrap',
        'clipboard', 'jquery-ui', 'moment-timezone', 'rxjs',
      ]
    };

    config.plugins = [
      new webpack.ContextReplacementPlugin(/angular(\\|\/)core(\\|\/)(esm(\\|\/)src|src)(\\|\/)linker/, __dirname),
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
          const chunks = ['init', 'vendor', 'settings', 'app'];
          return chunks.indexOf(a.names[0]) - chunks.indexOf(b.names[0]);
        }
      })
    ];
  }

  // this is temporary and will be deprecated in WP3.  moving forward,
  // loaders will individually need to accept this as an option.
  config.plugins.push(new webpack.LoaderOptionsPlugin({debug: !IS_TEST}));
  config.plugins.push(new CheckerPlugin());

  return config;
}

module.exports = configure;

const HtmlWebpackPlugin = require('html-webpack-plugin');
const webpack = require('webpack');
const HappyPack = require('happypack');
const path = require('path');
const NODE_MODULE_PATH = path.join(__dirname, 'node_modules');
const fs = require('fs');

function configure(IS_TEST) {

  const POOL_SIZE = IS_TEST ? 3 : 6;
  const happyThreadPool = HappyPack.ThreadPool({size: POOL_SIZE});
  function getTypescriptLinterLoader() {
    return {
      loader: 'tslint',
      test: /\.(spec\.)?ts$/
    };
  }

  function getJavascriptLinterLoader() {
    return {
      loader: 'eslint',
      test: /\.(spec\.)?js$/
    };
  }

  function getJavascriptLoader() {
    return {
      test: /\.js$/,
      exclude: /node_modules(?!\/clipboard)/,
      loader: IS_TEST ? 'happypack/loader?id=jstest' : 'happypack/loader?id=js'
    };
  }

  function getLessLoader() {
    return {
      test: /\.less$/,
      loader: IS_TEST ? 'style!css!less' : 'happypack/loader?id=less'
    };
  }

  function getHtmlLoader() {
    return {
      test: /\.html$/,
      loader: IS_TEST ? 'ngtemplate?relativeTo=' + __dirname + '/!html' : 'happypack/loader?id=html'
    };
  }

  const config = {
    debug: !IS_TEST,
    entry: IS_TEST ? {} : {
      settings: './settings.js',
      app: './app/scripts/app.js',
      vendor: [
        'jquery', 'angular', 'angular-ui-bootstrap', 'angular-ui-router',
        'source-sans-pro', 'angular-cache', 'angular-marked', 'angular-messages', 'angular-sanitize',
        'bootstrap', 'clipboard', 'd3', 'jquery-ui', 'moment-timezone', 'rxjs'
      ]
    },
    output: IS_TEST ? {} : {
      path: path.join(__dirname, 'build', 'webpack', process.env.SPINNAKER_ENV || ''),
      filename: '[name].js',
    },
    resolveLoader: IS_TEST ? {} : {
      root: NODE_MODULE_PATH
    },
    resolve: {
      extensions: ['', '.js', '.ts'],
      root: [
        NODE_MODULE_PATH,
        path.join(__dirname, 'app', 'scripts', 'modules'),
      ]
    },
    module: {
      preLoaders: [],
      loaders: [
        {
          test: /jquery\.js$/,
          loader: 'expose?jQuery',
        },
        {
          test: /\.ts$/,
          loader: 'ts',
          exclude: /node_modules/
        },
        {
          test: /\.css$/,
          loader: 'style!css',
        },
        {
          test: /\.(woff|otf|ttf|eot|svg|png|gif|ico)(.*)?$/,
          loader: 'file',
        },
        {
          test: /\.json$/,
          loader: 'json-loader',
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

  config.module.preLoaders.push(getTypescriptLinterLoader(), getJavascriptLinterLoader());
  config.module.loaders.push(getJavascriptLoader(), getLessLoader(), getHtmlLoader());

  if (IS_TEST) {

    config.plugins = [
      new HappyPack({
        id: 'jstest',
        loaders: ['ng-annotate!angular!babel!envify'],
        threadPool: happyThreadPool,
        cacheContext: {
          env: process.env,
        },
      })
    ];
  } else {

    config.plugins = [
      new webpack.optimize.CommonsChunkPlugin('vendor', 'vendor.bundle.js'),
      new webpack.optimize.CommonsChunkPlugin('init.js'),
      new HtmlWebpackPlugin({
        title: 'Spinnaker',
        template: './app/index.deck',
        favicon: 'app/favicon.ico',
        inject: true,
      }),
      new HappyPack({
        id: 'js',
        loaders: ['ng-annotate!angular!babel!envify'],
        threadPool: happyThreadPool,
        cacheContext: {
          env: process.env,
        },
      }),
      new HappyPack({
        id: 'html',
        loaders: ['ngtemplate?relativeTo=' + (path.resolve(__dirname)) + '/!html'],
        threadPool: happyThreadPool,
      }),
      new HappyPack({
        id: 'less',
        loaders: ['style!css!less'],
        threadPool: happyThreadPool,
      }),
    ];
  }

  return config;
}

module.exports = configure;

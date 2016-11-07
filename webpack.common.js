const HtmlWebpackPlugin = require('html-webpack-plugin');
const webpack = require('webpack');
const HappyPack = require('happypack');
const path = require('path');
const NODE_MODULE_PATH = path.join(__dirname, 'node_modules');

function configure(IS_TEST) {

  const POOL_SIZE = IS_TEST ? 3 : 6;
  const happyThreadPool = HappyPack.ThreadPool({size: POOL_SIZE});
  function getTypescriptLinterLoader() {
    return {
      loader: 'tslint',
      test: IS_TEST ? /\.spec.ts$/ : /\.ts$/
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
          query: {
            ignoreDiagnostics: [
              2300 // 2300 -> Duplicate identifier, needed or it'll barf on typings files
            ]
          },
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

  config.module.preLoaders.push(getTypescriptLinterLoader());
  config.module.loaders.push(getJavascriptLoader(), getLessLoader(), getHtmlLoader());

  if (IS_TEST) {

    config.module.postLoaders = [{
      test: /\.js$/,
      exclude: /(test|node_modules|bower_components)\//,
      loader: 'istanbul-instrumenter'
    }];

    config.plugins = [
      new HappyPack({
        id: 'jstest',
        loaders: ['ng-annotate!angular!babel!envify!eslint'],
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
        loaders: ['ng-annotate!angular!babel!envify!eslint'],
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

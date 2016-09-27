'use strict';

var HtmlWebpackPlugin = require('html-webpack-plugin');
var CommonsChunkPlugin = require('webpack/lib/optimize/CommonsChunkPlugin');
var HappyPack = require('happypack');
var happyThreadPool = HappyPack.ThreadPool({ size: 6 });
var path = require('path');

var nodeModulePath = path.join(__dirname, 'node_modules');

module.exports = {
  debug: true,
  entry: {
    settings: './settings.js',
    app: './app/scripts/app.js',
    vendor: ['jquery', 'angular', 'angular-animate', 'angular-ui-bootstrap', 'angular-ui-router',
      'source-sans-pro', 'angular-cache', 'angular-marked', 'angular-messages', 'angular-sanitize',
      'bootstrap', 'clipboard', 'd3', 'jquery-ui', 'moment-timezone', 'rxjs'
    ]
  },
  output: {
    path: path.join(__dirname, 'build', 'webpack', process.env.SPINNAKER_ENV || ''),
    filename: '[name].js',
  },
  module: {
    loaders: [
      {
        test: /jquery\.js$/,
        loader: 'expose?jQuery',
      },
      {
        test: /\.ts$/,
        loader: 'ts'
      },
      {
        test: /\.css$/,
        loader: 'style!css',
      },
      {
        test: /\.js$/,
        loader: 'happypack/loader?id=js',
        exclude: /node_modules(?!\/clipboard)/,
      },
      {
        test: /\.less$/,
        loader: 'happypack/loader?id=less',
      },
      {
        test: /\.(woff|otf|ttf|eot|svg|png|gif|ico)(.*)?$/,
        loader: 'file',
      },
      {
        test: /\.html$/,
        loader: 'happypack/loader?id=html',
      },
      {
        test: /\.json$/,
        loader: 'json-loader',
      }
    ],
  },
  resolveLoader: {
    root: nodeModulePath
  },
  plugins: [
    new CommonsChunkPlugin('vendor', 'vendor.bundle.js'),
    new CommonsChunkPlugin('init.js'),
    new HtmlWebpackPlugin({
      title: 'Spinnaker',
      template: './app/index.html',
      favicon: 'app/favicon.ico',
      inject: true,
    }),
    new HappyPack({
      id: 'js',
      loaders: [ 'ng-annotate!angular!babel!envify!eslint' ],
      threadPool: happyThreadPool,
      cacheContext: {
        env: process.env,
      },
    }),
    new HappyPack({
      id: 'html',
      loaders: [ 'ngtemplate?relativeTo=' + (path.resolve(__dirname))  + '/!html' ],
      threadPool: happyThreadPool,
    }),
    new HappyPack({
      id: 'less',
      loaders: [ 'style!css!less' ],
      threadPool: happyThreadPool,
    }),
  ],
  devServer: {
    port: process.env.DECK_PORT || 9000,
    host: process.env.DECK_HOST || 'localhost',
    https: process.env.DECK_HTTPS === 'true'
  }
};

'use strict';

var HtmlWebpackPlugin = require('html-webpack-plugin');
var CommonsChunkPlugin = require('webpack/lib/optimize/CommonsChunkPlugin');
//var webpack = require('webpack');
//var IgnorePlugin = require("webpack/lib/IgnorePlugin");
var path = require('path');

var nodeModulePath = path.join(__dirname, 'node_modules');
//var bowerModulePath = path.join(__dirname, 'bower_components');

module.exports = {
  debug: true,
  entry: {
    settings: './settings.js',
    app: './app/scripts/app.js',
    vendor: [
      'jquery',
      'lodash',
      'angular',
      'jquery-ui',
      'source-sans-pro',
      'Select2',
      'angulartics',
      'angular-animate',
      'angular-ui-router',
      'spin.js',
      'exports?"ui.bootstrap"!angular-bootstrap',
      'exports?"ui.select"!ui-select',
      'exports?"restangular"!imports?_=lodash!restangular',
      'exports?"angular.filter"!angular-filter',
      'exports?"infinite-scroll"!ng-infinite-scroll/build/ng-infinite-scroll.js',
      'bootstrap/dist/css/bootstrap.css'
    ],
  },
  output: {
    path: path.join(__dirname, 'build', 'webpack', process.env.SPINNAKER_ENV || ''),
    filename: '[name].js',

  },
  module: {

    //noParse: [
    //  /\.spec\.js$/,
    //],
    loaders: [
      {
        test: /jquery\.js$/,
        loader: 'expose?jQuery',
      },
      {
        test: /\.css$/,
        loader: 'style!css',
      },
      {
        test: /\.js$/,
        loader: 'ng-annotate!babel!envify!eslint',
        exclude: /node_modules(?!\/utils)/,
      },
      {
        test: /\.less$/,
        loader: 'style!css!less',
      },
      {
        test: /\.(woff|otf|ttf|eot|svg|png|gif|ico)(.*)?$/,
        loader: 'file',
      },
      {
        test: /\.html$/,
        loader: 'ngtemplate?relativeTo=' + (path.resolve(__dirname))  + '/!html'
      },
      {
        test: /\.json$/,
        loader: 'json-loader'
      }
    ],
  },
  resolve: {
    //root: [nodeModulePath, bowerModulePath],
    alias: {
      //lodash: 'utils/lodash.js'
      //angular: 'imports?window={}!exports?window.angular!angular/angular.js',
      //uiselect: 'angular-ui-select/dist/select.js'
    }
  },
  resolveLoader: {
    root: nodeModulePath
  },
  plugins: [
    new CommonsChunkPlugin('vendor', 'vendor.js'),
    new CommonsChunkPlugin(
      /* filename= */'init.js'
    ),
    new HtmlWebpackPlugin({
      title: 'Spinnaker',
      template: './app/index.html',
      favicon: 'app/favicon.ico',
      inject: true,
    }),
  ],
  devServer: {
    port: process.env.DECK_PORT || 9000,
    host: process.env.DECK_HOST || 'localhost'
  }
};

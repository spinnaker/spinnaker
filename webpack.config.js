'use strict';

var HtmlWebpackPlugin = require('html-webpack-plugin');
var IgnorePlugin = require("webpack/lib/IgnorePlugin");
var ResolverPlugin = require("webpack/lib/ResolverPlugin");
var path = require('path');

var nodeModulePath = path.join(__dirname, 'node_modules');
var bowerModulePath = path.join(__dirname, 'bower_components');

module.exports = {
  debug: true,
  entry: './app/scripts/app.js',
  devtool: 'eval',
  output: {
    path: path.join(__dirname, 'dist'),
    filename: 'bundle.js',

  },
  module: {

    noParse: [
      /\.spec\.js$/,
    ],
    loaders: [
      {
        test: /\.css$/,
        loader: 'style!css',
      },
      {
        test: /\.js$/,
        loader: 'babel!eslint',
        exclude: /node_modules/,
      },
      {
        test: /\.less$/,
        loader: 'style!css!less',
      },
      {
        test: /\.woff(2)?(.*)?$/,
        loader: 'url?limit=10000&minetype=application/font-woff',
      },
      {
        test: /\.(otf|ttf|eot|svg)(.*)?$/,
        loader: 'file',
      },
      {
        test: /\.html$/,
        loader: 'ngtemplate?relativeTo=' + __dirname  + '/!html'
      },
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
    new IgnorePlugin(
      /\.spec/
    ),
    new HtmlWebpackPlugin({
      title: 'Spinnaker',
      template: './app/index.html',
      inject: true,
    }),
  ],
};

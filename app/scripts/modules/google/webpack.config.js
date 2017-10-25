'use strict';

const path = require('path');
const basePath = path.join(__dirname, '..', '..', '..', '..');
const NODE_MODULE_PATH = path.join(basePath, 'node_modules');
const ForkTsCheckerWebpackPlugin = require('fork-ts-checker-webpack-plugin');
const nodeExternals = require('webpack-node-externals');
const webpack = require('webpack');
const exclusionPattern = /(node_modules|\.\.\/deck)/;

module.exports = {
  context: basePath,
  stats: 'errors-only',
  devtool: "source-map",
  entry: {
    lib: path.join(__dirname, 'src', 'index.ts'),
  },
  output: {
    path: path.join(__dirname, 'lib'),
    filename: '[name].js',
    library: '@spinnaker/google',
    libraryTarget: 'umd',
    umdNamedDefine: true,
  },
  externals: [
    '@spinnaker/core',
    nodeExternals({ modulesDir: '../../../../node_modules' }),
  ],
  resolve: {
    extensions: ['.json', '.js', '.jsx', '.ts', '.tsx', '.css', '.less', '.html'],
    modules: [
      NODE_MODULE_PATH,
      path.resolve('.'),
    ],
    alias: {
      '@spinnaker/google': path.join(__dirname, 'src'),
      'coreImports': path.resolve(basePath, 'app', 'scripts', 'modules', 'core', 'src', 'presentation', 'less', 'imports', 'commonImports.less'),
      'google': path.join(__dirname, 'src')
    }
  },
  watch:  process.env.WATCH === 'true',
  module: {
    rules: [
      {
        test: /\.js$/,
        use: [
          { loader: 'cache-loader' },
          { loader: 'thread-loader', options: { workers: 3 } },
          { loader: 'babel-loader' },
          { loader: 'envify-loader' },
          { loader: 'eslint-loader' } ,
        ],
        exclude: exclusionPattern
      },
      {
        test: /\.tsx?$/,
        use: [
          { loader: 'cache-loader' },
          { loader: 'thread-loader', options: { workers: 3 } },
          { loader: 'babel-loader' },
          { loader: 'ts-loader', options: { happyPackMode: true } },
          { loader: 'tslint-loader' },
        ],
        exclude: exclusionPattern
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
        exclude: exclusionPattern,
        use: [
          { loader: 'ngtemplate-loader?relativeTo=' + (path.resolve(__dirname)) + '&prefix=google' },
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
    ],
  },
  plugins: [
    new ForkTsCheckerWebpackPlugin({ checkSyntacticErrors: true }),
    new webpack.optimize.UglifyJsPlugin({
      mangle: false,
      beautify: true,
      comments: false,
      sourceMap: true,
    }),
  ],
};

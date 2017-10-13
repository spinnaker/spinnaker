'use strict';

const path = require('path');
const basePath = path.join(__dirname, '..');
const NODE_MODULE_PATH = path.join(basePath, 'node_modules');
const HappyPack = require('happypack');
const HAPPY_PACK_POOL_SIZE = process.env.HAPPY_PACK_POOL_SIZE || 3;
const happyThreadPool = HappyPack.ThreadPool({size: HAPPY_PACK_POOL_SIZE});
const ForkTsCheckerWebpackPlugin = require('fork-ts-checker-webpack-plugin');
const nodeExternals = require('webpack-node-externals');
const webpack = require('webpack');
const exclusionPattern = /(node_modules|\.\.\/deck)/;

module.exports = {
  context: basePath,
  entry: {
    lib: path.join(basePath, 'src', 'kayenta', 'index.ts'),
  },
  output: {
    path: path.join(basePath, 'lib'),
    filename: '[name].js',
    library: '@spinnaker/kayenta',
    libraryTarget: 'umd',
    umdNamedDefine: true,
  },
  externals: [
    nodeExternals({ modulesDir: NODE_MODULE_PATH }),
  ],
  resolve: {
    extensions: ['.json', '.js', '.jsx', '.ts', '.tsx', '.css', '.less', '.html'],
    modules: [
      NODE_MODULE_PATH,
      path.resolve('.'),
    ],
    alias: {
      'kayenta': path.join(basePath, 'src', 'kayenta'),
      'coreImports': path.resolve(NODE_MODULE_PATH, '@spinnaker', 'core', 'src', 'presentation', 'less', 'imports', 'commonImports.less'),
    }
  },
  devtool: 'source-map',
  watch:  process.env.WATCH === 'true',
  module: {
    rules: [
      {test: /\.js$/, use: ['happypack/loader?id=js'], exclude: exclusionPattern},
      {test: /\.tsx?$/, use: ['happypack/loader?id=ts'], exclude: exclusionPattern},
      {test: /\.json$/, loader: 'json-loader'},
      {test: /\.(woff|otf|ttf|eot|png|gif|ico|svg)(.*)?$/, use: 'file-loader'},
      {
        test: require.resolve('jquery'),
        use: [
          'expose-loader?$',
          'expose-loader?jQuery'
        ]
      },
      {
        test: /\.less$/,
        use: ['happypack/loader?id=less']
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
        use: ['happypack/loader?id=lib-html'],
        exclude: exclusionPattern,
      }
    ],
  },
  plugins: [
    new ForkTsCheckerWebpackPlugin({ checkSyntacticErrors: true }),
    new webpack.optimize.UglifyJsPlugin({
      mangle: false,
      beautify: true,
      comments: true,
      sourceMap: true,
    }),
    new HappyPack({
      id: 'lib-html',
      loaders: [
        'ngtemplate-loader?relativeTo=' + (path.resolve(basePath, 'src')) + '&prefix=kayenta',
        'html-loader'
      ],
      threadPool: happyThreadPool
    }),
    new HappyPack({
      id: 'js',
      loaders: [
        'babel-loader',
        'envify-loader',
        'eslint-loader',
      ],
      threadPool: happyThreadPool,
    }),
    new HappyPack({
      id: 'ts',
      loaders: [
        'babel-loader',
        { path: 'ts-loader', query: { happyPackMode: true } },
        'tslint-loader',
      ],
      threadPool: happyThreadPool,
    }),
    new HappyPack({
      id: 'less',
      loaders: [
        'style-loader',
        'css-loader',
        'less-loader'
      ],
      threadPool: happyThreadPool
    }),
  ],
};

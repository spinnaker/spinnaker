'use strict';

const path = require('path');
const basePath = path.join(__dirname, '..');
const NODE_MODULE_PATH = path.join(basePath, 'node_modules');
const ForkTsCheckerWebpackPlugin = require('fork-ts-checker-webpack-plugin');
const nodeExternals = require('webpack-node-externals');
const exclusionPattern = /(node_modules|\.\.\/deck)/;

module.exports = {
  context: basePath,
  entry: {
    lib: [path.join(basePath, 'src', 'kayenta', 'index.ts')],
  },
  output: {
    path: path.join(basePath, 'lib'),
    filename: '[name].js',
    library: '@spinnaker/kayenta',
    libraryTarget: 'umd',
    umdNamedDefine: true,
  },
  externals: [
    nodeExternals({
      modulesDir: NODE_MODULE_PATH,
    }),
  ],
  resolve: {
    extensions: ['.json', '.js', '.jsx', '.ts', '.tsx', '.css', '.less', '.html'],
    modules: [NODE_MODULE_PATH, path.resolve('.')],
    alias: {
      kayenta: path.join(basePath, 'src', 'kayenta'),
      coreImports: path.resolve(
        NODE_MODULE_PATH,
        '@spinnaker',
        'core',
        'src',
        'presentation',
        'less',
        'imports',
        'commonImports.less',
      ),
    },
  },
  devtool: 'source-map',
  watch: process.env.WATCH === 'true',
  module: {
    rules: [
      { test: /\.js$/, use: ['envify-loader', 'eslint-loader'], exclude: exclusionPattern },
      { test: /\.tsx?$/, use: ['ts-loader', 'eslint-loader'], exclude: exclusionPattern },
      { test: /\.(woff|otf|ttf|eot|png|gif|ico|svg)(.*)?$/, use: 'file-loader' },
      {
        test: require.resolve('jquery'),
        use: ['expose-loader?$', 'expose-loader?jQuery'],
      },
      {
        test: /\.less$/,
        use: ['style-loader', 'css-loader', 'less-loader'],
      },
      {
        test: /\.css$/,
        use: ['style-loader', 'css-loader', 'postcss-loader'],
      },
      {
        test: /\.html$/,
        use: ['ngtemplate-loader?relativeTo=' + path.resolve(basePath, 'src') + '&prefix=kayenta', 'html-loader'],
        exclude: exclusionPattern,
      },
    ],
  },
  plugins: [new ForkTsCheckerWebpackPlugin({ checkSyntacticErrors: true })],
};

'use strict';

const path = require('path');
const nodeExternals = require('webpack-node-externals');
const TerserPlugin = require('terser-webpack-plugin');
const TsconfigPathsPlugin = require('tsconfig-paths-webpack-plugin');

const exclusionPattern = /(node_modules|\.\.\/deck)/;
const WEBPACK_THREADS = Math.max(require('physical-cpu-count') - 1, 1);

const WATCH = process.env.WATCH === 'true';
const WEBPACK_MODE = WATCH ? 'development' : 'production';
const IS_PRODUCTION = WEBPACK_MODE === 'production';

// libName should match the folder name
module.exports.getBaseConfig = (libName) => {
  return {
    context: path.join(__dirname, '..', '..', '..'),
    mode: WEBPACK_MODE,
    stats: 'minimal',
    watch: WATCH,
    entry: {
      lib: path.join(__dirname, libName, 'src', 'index.ts'),
    },
    output: {
      path: path.join(__dirname, libName, 'lib'),
      filename: '[name].js',
      library: `@spinnaker/${libName}`,
      libraryTarget: 'umd',
      umdNamedDefine: true,
    },
    devtool: 'source-map',
    optimization: {
      minimizer: IS_PRODUCTION
        ? [
            new TerserPlugin({
              cache: true,
              parallel: true,
              sourceMap: true,
              terserOptions: {
                ecma: 6,
                mangle: false,
                output: {
                  comments: /webpackIgnore/,
                },
              },
            }),
          ]
        : [], // disable minification in development mode
    },
    resolve: {
      extensions: ['.json', '.js', '.jsx', '.ts', '.tsx', '.css', '.less', '.html'],
      plugins: [new TsconfigPathsPlugin({ logLevel: 'info', extensions: ['.ts', '.tsx', '.js', '.jsx'] })],
      alias: {
        coreImports: path.resolve(__dirname, 'core', 'src', 'presentation', 'less', 'imports', 'commonImports.less'),
      },
    },
    module: {
      rules: [
        {
          test: /\.js$/,
          use: [
            { loader: 'cache-loader' },
            { loader: 'thread-loader', options: { workers: WEBPACK_THREADS } },
            { loader: 'babel-loader' },
            { loader: 'envify-loader' },
            { loader: 'eslint-loader' },
          ],
          exclude: exclusionPattern,
        },
        {
          test: /\.tsx?$/,
          use: [
            { loader: 'cache-loader' },
            { loader: 'thread-loader', options: { workers: WEBPACK_THREADS } },
            { loader: 'ts-loader', options: { happyPackMode: true } },
            { loader: 'eslint-loader' },
          ],
          exclude: exclusionPattern,
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
          use: [{ loader: 'style-loader' }, { loader: 'css-loader' }, { loader: 'postcss-loader' }],
        },
        {
          test: /\.svg$/,
          issuer: {
            test: /\.(tsx?|js)$/,
          },
          use: [{ loader: '@svgr/webpack' }],
          exclude: exclusionPattern,
        },
        {
          test: /\.html$/,
          exclude: exclusionPattern,
          use: [
            { loader: 'ngtemplate-loader?relativeTo=' + path.resolve(__dirname, libName) + `&prefix=${libName}` },
            { loader: 'html-loader' },
          ],
        },
        {
          test: /\.(woff|woff2|otf|ttf|eot|png|gif|ico|svg)$/,
          use: [{ loader: 'file-loader', options: { name: '[name].[hash:5].[ext]' } }],
        },
        {
          test: require.resolve('jquery'),
          use: [{ loader: 'expose-loader?$' }, { loader: 'expose-loader?jQuery' }],
        },
      ],
    },
    plugins: [],
    externals: [nodeExternals({ modulesDir: '../../../../node_modules' })],
  };
};

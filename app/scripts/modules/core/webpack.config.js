'use strict';

const path = require('path');
const fs = require('fs');
const corePath = __dirname;
const basePath = path.join(__dirname, '..', '..', '..', '..');
const NODE_MODULE_PATH = path.join(basePath, 'node_modules');
const nodeExternals = require('webpack-node-externals');
const TerserPlugin = require('terser-webpack-plugin');
const CopyWebpackPlugin = require('copy-webpack-plugin');
const exclusionPattern = /(node_modules|\.\.\/deck)/;
const WEBPACK_THREADS = Math.max(require('physical-cpu-count') - 1, 1);

const WATCH = process.env.WATCH === 'true';
const WEBPACK_MODE = WATCH ? 'development' : 'production';
const IS_PRODUCTION = WEBPACK_MODE === 'production';

// In index.ts, the triple slash reference to global namespace types get re-written to match the 'core' alias
// This kludge re-re-writes them back to a relative path
// Likely related to https://github.com/microsoft/TypeScript/issues/36763
class KLUDGE_FixTypesReferencePlugin {
  apply(compiler) {
    compiler.hooks.afterEmit.tap('KLUDGE_FixTypesReferencePlugin', (file, rest) => {
      // eslint-disable-next-line no-console
      console.log('Fixing up core/index.d.ts...');
      const dts = path.join(corePath, 'lib', 'index.d.ts');
      const fixedup = fs
        .readFileSync(dts)
        .toString()
        .replace(/types="core\/types"/, 'types="./types"');
      fs.writeFileSync(dts, fixedup);
    });
  }
}

module.exports = {
  context: basePath,
  mode: WEBPACK_MODE,
  stats: 'minimal',
  watch: WATCH,
  entry: {
    lib: path.join(__dirname, 'src', 'index.ts'),
  },
  output: {
    path: path.join(__dirname, 'lib'),
    filename: '[name].js',
    library: '@spinnaker/core',
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
    modules: [NODE_MODULE_PATH, path.resolve('.')],
    alias: {
      '@spinnaker/core': path.join(__dirname, 'src'),
      core: path.join(__dirname, 'src'),
      root: basePath,
      coreImports: path.resolve(__dirname, 'src', 'presentation', 'less', 'imports', 'commonImports.less'),
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
          { loader: 'ngtemplate-loader?relativeTo=' + path.resolve(__dirname) + '&prefix=core' },
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
  plugins: [
    new CopyWebpackPlugin([{ from: `${corePath}/src/types`, to: `types` }]),
    new KLUDGE_FixTypesReferencePlugin(),
  ],
  externals: ['root/version.json', nodeExternals({ modulesDir: '../../../../node_modules' })],
};

'use strict';

const path = require('path');
const basePath = path.join(__dirname, '..', '..', '..', '..');
const NODE_MODULE_PATH = path.join(basePath, 'node_modules');
const HappyPack = require('happypack');
const HAPPY_PACK_POOL_SIZE = process.env.HAPPY_PACK_POOL_SIZE || 3;
const happyThreadPool = HappyPack.ThreadPool({size: HAPPY_PACK_POOL_SIZE});
const ForkTsCheckerWebpackPlugin = require('fork-ts-checker-webpack-plugin');
const webpack = require('webpack');
const exclusionPattern = /(node_modules|\.\.\/deck)/;

module.exports = {
  context: basePath,
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
  externals: {
    'root/version.json': 'root/version.json',
    '@uirouter/angularjs': '@uirouter/angularjs',
    '@uirouter/core': '@uirouter/core',
    '@uirouter/react': '@uirouter/react',
    '@uirouter/react-hybrid': '@uirouter/react-hybrid',
    '@uirouter/visualizer': '@uirouter/visualizer',
    'angular': 'angular',
    'angular-animate': 'angular-animate',
    'angular-cache': 'angular-cache',
    'angular-cron-gen': 'angular-cron-gen',
    'angular-messages': 'angular-messages',
    'angular-sanitize': 'angular-sanitize',
    'angular-spinner': 'angular-spinner',
    'angular-ui-bootstrap': 'angular-ui-bootstrap',
    'angular-ui-sortable': 'angular-ui-sortable',
    'angular2react': 'angular2react',
    'angulartics': 'angulartics',
    'angulartics-google-analytics': 'angulartics-google-analytics',
    'autoprefixer': 'autoprefixer',
    'bootstrap': 'bootstrap',
    'bootstrap/dist/js/bootstrap.js': 'bootstrap/dist/js/bootstrap.js',
    'bootstrap/dist/css/bootstrap.css': 'bootstrap/dist/css/bootstrap.css',
    'class-autobind-decorator': 'class-autobind-decorator',
    'clipboard': 'clipboard',
    'commonmark': 'commonmark',
    'cssnano': 'cssnano',
    'd3-scale': 'd3-scale',
    'd3-shape': 'd3-shape',
    'expose-loader?diff_match_patch!diff-match-patch': 'expose-loader?diff_match_patch!diff-match-patch',
    'dompurify': 'dompurify',
    'font-awesome': 'font-awesome',
    'font-awesome/css/font-awesome.css': 'font-awesome/css/font-awesome.css',
    'formsy-react': 'formsy-react',
    'jquery': 'jquery',
    'jquery-textcomplete': 'jquery-textcomplete',
    'jquery-ui': 'jquery-ui',
    'js-yaml': 'js-yaml',
    'later': 'later',
    'lodash': 'lodash',
    'lodash-decorators': 'lodash-decorators',
    'moment': 'moment',
    'moment-timezone': 'moment-timezone',
    'ngimport': 'ngimport',
    'postcss-loader': 'postcss-loader',
    'postcss-import': 'postcss-import',
    'postcss-extend': 'postcss-extend',
    'postcss-style-guide': 'postcss-style-guide',
    'postcss-scopeit': 'postcss-scopeit',
    'prop-types': 'prop-types',
    'react': 'react',
    'react-bootstrap': 'react-bootstrap',
    'react-dom': 'react-dom',
    'react-ga': 'react-ga',
    'react2angular': 'react2angular',
    'react-select': 'react-select',
    'react-select/dist/react-select.css': 'react-select/dist/react-select.css',
    'react-virtualized': 'react-virtualized',
    'react-virtualized/styles.css': 'react-virtualized/styles.css',
    'react-virtualized-select/styles.css': 'react-virtualized-select/styles.css',
    'rxjs': 'rxjs',
    'Select2': 'Select2',
    'Select2/select2.css': 'Select2/select2.css',
    'select2-bootstrap-css/select2-bootstrap.css': 'select2-bootstrap-css/select2-bootstrap.css',
    'source-sans-pro': 'source-sans-pro',
    'spin.js': 'spin.js',
    'svg-react-loader': 'svg-react-loader',
    'ui-select': 'ui-select',
  },
  resolve: {
    extensions: ['.json', '.js', '.jsx', '.ts', '.tsx', '.css', '.less', '.html'],
    modules: [
      NODE_MODULE_PATH,
      path.resolve('.'),
    ],
    alias: {
      '@spinnaker/core': path.join(__dirname, 'src'),
      'core': path.join(__dirname, 'src'),
      'root': basePath,
      'coreImports': path.resolve(__dirname, 'src', 'presentation', 'less', 'imports', 'commonImports.less'),
    }
  },
  devtool: 'source-map',
  watch:  process.env.WATCH === 'true',
  module: {
    rules: [
      {test: /\.js$/, use: ['happypack/loader?id=js'], exclude: exclusionPattern},
      {test: /\.tsx?$/, use: ['happypack/loader?id=ts'], exclude: exclusionPattern},
      {test: /\.json$/, loader: 'json-loader'},
      {test: /\.(woff|otf|ttf|eot|png|gif|ico)(.*)?$/, use: 'file-loader'},
      {
        test: /\.svg(.*)?$/,
        exclude: /\/src\/widgets\/spinners/,
        use: 'file-loader'
      },
      {
        test: /\.svg(.*)?$/,
        include: /\/src\/widgets\/spinners/,
        use: ['svg-react-loader']
      },
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
      sourceMap: false,
    }),
    new HappyPack({
      id: 'lib-html',
      loaders: [
        'ngtemplate-loader?relativeTo=' + (path.resolve(__dirname)) + '&prefix=core',
        'html-loader'
      ],
      threadPool: happyThreadPool
    }),
    new HappyPack({
      id: 'js',
      loaders: [
        'angular-loader',
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

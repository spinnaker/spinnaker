'use strict';

const path = require('path');
const basePath = path.join(__dirname, '..', '..', '..', '..');
const NODE_MODULE_PATH = path.join(basePath, 'node_modules');
const HappyPack = require('happypack');
const happyThreadPool = HappyPack.ThreadPool({ size: 3 });
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
    '@uirouter/angularjs': '@uirouter/angularjs',
    '@uirouter/core': '@uirouter/core',
    'angular': 'angular',
    'angular-animate': 'angular-animate',
    'angular-cache': 'angular-cache',
    'angular-cron-gen': 'angular-cron-gen',
    'angular-marked': 'angular-marked',
    'angular-messages': 'angular-messages',
    'angular-sanitize': 'angular-sanitize',
    'angular-spinner': 'angular-spinner',
    'angular-ui-bootstrap': 'angular-ui-bootstrap',
    'angular-ui-router': { root: 'angular-ui-router', amd: 'angular-ui-router', commonjs2: 'angular-ui-router', commonjs: 'angular-ui-router' },
    'angular-ui-sortable': 'angular-ui-sortable',
    'angular2react': 'angular2react',
    'angulartics': 'angulartics',
    'angulartics-google-analytics': 'angulartics-google-analytics',
    'bootstrap': 'bootstrap',
    'bootstrap/dist/js/bootstrap.js': 'bootstrap/dist/js/bootstrap.js',
    'bootstrap/dist/css/bootstrap.css': 'bootstrap/dist/css/bootstrap.css',
    'class-autobind-decorator': 'class-autobind-decorator',
    'clipboard': 'clipboard',
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
    'marked': 'marked',
    'moment': 'moment',
    'moment-timezone': 'moment-timezone',
    'ngimport': 'ngimport',
    'prop-types': 'prop-types',
    'react': 'react',
    'react-bootstrap': 'react-bootstrap',
    'react-dom': 'react-dom',
    'react-ga': 'react-ga',
    'react2angular': 'react2angular',
    'react-select': 'react-select',
    'react-select/dist/react-select.css': 'react-select/dist/react-select.css',
    'rxjs': 'rxjs',
    'Select2': 'Select2',
    'Select2/select2.css': 'Select2/select2.css',
    'select2-bootstrap-css/select2-bootstrap.css': 'select2-bootstrap-css/select2-bootstrap.css',
    'source-sans-pro': 'source-sans-pro',
    'spin.js': 'spin.js',
    'ui-router-visualizer': 'ui-router-visualizer',
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
    }
  },
  watch:  process.env.WATCH === 'true',
  module: {
    rules: [
      {enforce: 'pre', test: /\.(spec\.)?tsx?$/, use: 'tslint-loader', exclude: exclusionPattern},
      {enforce: 'pre', test: /\.(spec\.)?js$/, loader: 'eslint-loader', exclude: exclusionPattern},
      {test: /\.json$/, loader: 'json-loader'},
      {test: /\.tsx?$/, use: [
        'ng-annotate-loader',
        { loader: 'awesome-typescript-loader', options: { babelCore: path.join(NODE_MODULE_PATH, 'babel-core') } }
      ],
        exclude: exclusionPattern},
      {test: /\.(woff|otf|ttf|eot|svg|png|gif|ico)(.*)?$/, use: 'file-loader'},
      {test: /\.js$/, use: ['happypack/loader?id=js'], exclude: exclusionPattern},
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
          'css-loader'
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
        'ng-annotate-loader',
        'angular-loader',
        'babel-loader',
        'envify-loader',
        'eslint-loader'
      ],
      threadPool: happyThreadPool,
      cacheContext: {
        env: process.env
      }
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

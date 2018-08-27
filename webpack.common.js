const webpack = require('webpack');
const HtmlWebpackPlugin = require('html-webpack-plugin');
const CopyWebpackPlugin = require('copy-webpack-plugin');

const path = require('path');
const NODE_MODULE_PATH = path.join(__dirname, 'node_modules');
const inclusionPattern = [
  path.resolve(__dirname, 'src'),
  path.resolve(NODE_MODULE_PATH, '@spinnaker/styleguide'),
];

function configure(IS_TEST) {

  const config = {
    plugins: [],
    output: IS_TEST ? undefined : {
      path: path.join(__dirname, 'build', 'webpack', process.env.SPINNAKER_ENV || ''),
      filename: '[name].js',
    },
    resolveLoader: IS_TEST ? {} : {
      modules: [
        NODE_MODULE_PATH
      ],
      moduleExtensions: ['-loader']
    },
    resolve: {
      extensions: ['.json', '.js', '.jsx', '.ts', '.tsx', '.css', '.less', '.html'],
      modules: [
        NODE_MODULE_PATH,
        path.join(__dirname, 'src'),
      ],
      alias: {
        'coreImports': path.resolve(NODE_MODULE_PATH, '@spinnaker', 'core', 'src', 'presentation', 'less', 'imports', 'commonImports.less'),
        'root': __dirname,
      }
    },
    module: {
      rules: [
        {enforce: 'pre', test: /\.(spec\.)?tsx?$/, use: 'tslint-loader', include: inclusionPattern},
        {enforce: 'pre', test: /\.(spec\.)?js$/, loader: 'eslint-loader', include: inclusionPattern},
        {test: /\.(woff|otf|ttf|eot|svg|png|gif|ico)(.*)?$/, use: 'file-loader'},
        {test: /\.json$/, loader: 'json-loader', include: inclusionPattern},
        {test: require.resolve('jquery'), use: ['expose-loader?$', 'expose-loader?jQuery']},
        {test: /\.tsx?$/, use: ['cache-loader', 'ng-annotate-loader', 'awesome-typescript-loader'], include: inclusionPattern.concat(path.join(__dirname, 'test'))},
        {
          test: /\.js$/,
          use: [
            'cache-loader',
            'ng-annotate-loader',
            'babel-loader',
            'eslint-loader'
          ],
          include: inclusionPattern.concat(path.resolve(__dirname, 'settings.js'))
        },
        {
          test: /\.js$/,
          use: ['ify-loader', 'transform-loader?plotly.js/tasks/util/compress_attributes.js'],
          exclude: /node_modules\/react/
        },
        {
          test: /\.less$/,
          use: ['cache-loader', 'style-loader', 'css-loader', 'less-loader'],
          include: inclusionPattern
        },
        {
          test: /\.css$/,
          use: [
            'cache-loader',
            'style-loader',
            'css-loader'
          ],
          // include: inclusionPattern
        },
        {
          test: /\.html$/,
          use: [
            'cache-loader',
            'ngtemplate-loader?relativeTo=' + (path.resolve(__dirname)) + '/',
            'html-loader'
          ],
          include: inclusionPattern
        }
      ],
    },
    devServer: IS_TEST ? {} : {
      disableHostCheck: true,
      port: process.env.DECK_PORT || 9000,
      host: process.env.DECK_HOST || 'localhost',
      https: process.env.DECK_HTTPS === 'true'
    },
    watch: IS_TEST,
    externals: {
      'react/addons': 'react',
      'react/lib/ExecutionEnvironment': 'react',
      'react/lib/ReactContext': 'react',
    },
    node: {
      fs: 'empty',
    }
  };

  if (!IS_TEST) {
    config.entry = {
      settings: './settings.js',
      app: './src/app.ts',
      vendor: [
        'jquery', 'angular', 'angular-ui-bootstrap', 'source-sans-pro',
        'angular-cache', 'angular-messages', 'angular-sanitize', 'bootstrap',
        'clipboard', 'd3', 'jquery-ui', 'moment-timezone', 'rxjs', 'react', 'angular2react',
        'react2angular', 'react-bootstrap', 'react-dom', 'react-ga', 'ui-router-visualizer', 'ui-select',
        '@uirouter/angularjs', 'babel-polyfill'
      ],
      spinnaker: ['@spinnaker/core']
    };

    config.plugins.push(...[
      new webpack.optimize.CommonsChunkPlugin({ names: ['app', 'spinnaker', 'settings', 'vendor', 'manifest'] }),
      new CopyWebpackPlugin(
        [
          { from: 'node_modules/@spinnaker/core/lib' },
        ],
        { copyUnmodified: false, ignore: ['*.js', '*.ts', '*.map', 'index.html'] }
      ),
      new HtmlWebpackPlugin({
        title: 'Spinnaker',
        template: './index.deck',
        favicon: 'favicon.ico',
        inject: true,
      }),
      new webpack.EnvironmentPlugin({
        API_HOST: 'https://api-prestaging.spinnaker.mgmt.netflix.net',
        ATLAS_WEB_COMPONENTS_URL: '',
        CANARY_ACCOUNT: 'my-google-account',
        CANARY_STAGES_ENABLED: true,
        ENTITY_TAGS_ENABLED: true,
        FEEDBACK_URL: 'https://hootch.test.netflix.net/submit',
        FIAT_ENABLED: false,
        INFRA_STAGES: false,
        METRIC_STORE: 'atlas',
        REDUX_LOGGER: false,
        TEMPLATES_ENABLED: false,
        TIMEZONE: 'America/Los_Angeles',
      }),
    ]);
  }

  // this is temporary and will be deprecated in WP3.  moving forward,
  // loaders will individually need to accept this as an option.
  config.plugins.push(new webpack.LoaderOptionsPlugin({debug: !IS_TEST}));

  return config;
}

module.exports = configure;

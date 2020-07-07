const webpack = require('webpack');
const HtmlWebpackPlugin = require('html-webpack-plugin');
const CopyWebpackPlugin = require('copy-webpack-plugin');

const path = require('path');
const NODE_MODULE_PATH = path.join(__dirname, 'node_modules');
const inclusionPattern = [path.resolve(__dirname, 'src'), path.resolve(NODE_MODULE_PATH, '@spinnaker/styleguide')];

function configure(IS_TEST, IS_INSTRUMENTED) {
  const config = {
    mode: IS_TEST ? 'development' : 'production',
    plugins: [],
    output: IS_TEST
      ? undefined
      : {
          path: path.join(__dirname, 'build', 'webpack', process.env.SPINNAKER_ENV || ''),
          filename: '[name].js',
        },
    resolveLoader: IS_TEST
      ? {}
      : {
          modules: [NODE_MODULE_PATH],
          moduleExtensions: ['-loader'],
        },
    resolve: {
      extensions: ['.json', '.js', '.jsx', '.ts', '.tsx', '.css', '.less', '.html'],
      modules: [NODE_MODULE_PATH, path.join(__dirname, 'src')],
      alias: {
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
        root: __dirname,
      },
    },
    optimization: {
      splitChunks: {
        chunks: 'all', // enables splitting of both initial and async chunks
        maxInitialRequests: 10, // allows up to 10 initial chunks
        cacheGroups: {
          // Put code matching each regexp in a separate chunk
          app: new RegExp('/src'),
          spinnakercore: /node_modules\/@spinnaker\/core/,
          vendor: new RegExp('node_modules/(?!@spinnaker/core)'),
        },
      },
    },
    module: {
      rules: [
        { enforce: 'pre', test: /\.(spec\.)?tsx?$/, loader: 'eslint-loader', include: inclusionPattern },
        { test: /\.(woff|otf|ttf|eot|svg|png|gif|ico)(.*)?$/, use: 'file-loader' },
        { test: /\.json$/, loader: 'json-loader', include: inclusionPattern },
        { test: require.resolve('jquery'), use: ['expose-loader?$', 'expose-loader?jQuery'] },
        {
          test: /\.js$/,
          use: [{ loader: 'source-map-loader' }],
          enforce: 'pre',
          include: /node_modules.*(netflix|spinnaker|uirouter)/,
          exclude: [/\/angular-cache.js/],
        },
        {
          test: /\.js$/,
          use: ['cache-loader', 'eslint-loader'],
          include: inclusionPattern.concat(path.resolve(__dirname, 'settings.js')),
        },
        {
          test: /\.d\.ts$/,
          use: ['null-loader'],
        },
        {
          test: /\.less$/,
          use: ['cache-loader', 'style-loader', 'css-loader', 'less-loader'],
          include: inclusionPattern,
        },
        {
          test: /\.css$/,
          use: ['cache-loader', 'style-loader', 'css-loader'],
        },
        {
          test: /\.html$/,
          use: ['cache-loader', 'ngtemplate-loader?relativeTo=' + path.resolve(__dirname) + '/', 'html-loader'],
          include: inclusionPattern,
        },
      ],
    },
    devServer: IS_TEST
      ? {}
      : {
          disableHostCheck: true,
          port: process.env.DECK_PORT || 9000,
          host: process.env.DECK_HOST || 'localhost',
          https: process.env.DECK_HTTPS === 'true',
        },
    watch: IS_TEST,
    externals: {
      'react/addons': 'react',
      'react/lib/ExecutionEnvironment': 'react',
      'react/lib/ReactContext': 'react',
    },
    node: {
      fs: 'empty',
    },
  };

  if (!IS_TEST) {
    config.entry = {
      settings: './settings.js',
      app: './src/app.ts',
    };

    config.plugins.push(
      ...[
        new CopyWebpackPlugin(
          [
            { from: 'node_modules/@spinnaker/core/lib' },
            { from: `./plugin-manifest.json`, to: `./plugin-manifest.json` },
          ],
          {
            copyUnmodified: false,
            ignore: ['*.js', '*.ts', '*.map', 'index.html'],
          },
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
      ],
    );
  }

  if (IS_INSTRUMENTED) {
    config.module.rules.push(
      {
        enforce: 'pre',
        test: /\.js$/,
        loader: 'source-map-loader',
        include: path.resolve(__dirname, 'src'),
        exclude: /\.spec\.(js|ts|tsx)$/,
      },
      {
        test: /\.tsx?$/,
        use: [{ loader: 'ts-loader' }],
        exclude: /\.d\.ts$/,
      },
      {
        enforce: 'post',
        test: /\.(js|ts|tsx)$/,
        loader: 'istanbul-instrumenter-loader',
        options: {
          esModules: true,
        },
        include: path.resolve(__dirname, 'src'),
        exclude: /\.(d|spec)\.(js|ts|tsx)$/,
      },
    );
  } else {
    config.module.rules.push({
      test: /\.tsx?$/,
      use: ['cache-loader', 'ts-loader', 'eslint-loader'],
      include: inclusionPattern.concat(path.join(__dirname, 'test')),
      exclude: /\.d\.ts$/,
    });
  }

  // this is temporary and will be deprecated in WP3.  moving forward,
  // loaders will individually need to accept this as an option.
  config.plugins.push(new webpack.LoaderOptionsPlugin({ debug: !IS_TEST }));

  return config;
}

module.exports = configure;

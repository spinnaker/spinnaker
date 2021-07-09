const createCompiler = require('@storybook/addon-docs/mdx-compiler-plugin');

const deckWebpackConfigurer = require('../webpack.config');

module.exports = {
  addons: ['@storybook/preset-typescript', '@storybook/addon-essentials', '@storybook/addon-storysource'],
  stories: [
    '../packages/core/src/presentation/**/*.stories.[tj]sx',
    '../packages/core/src/presentation/**/*.stories.mdx',
  ],
  webpackFinal: async (config) => {
    const deckWebpackConfig = deckWebpackConfigurer({}, {});

    config.resolve = {
      ...config.resolve,
      alias: deckWebpackConfig.resolve.alias,
      extensions: deckWebpackConfig.resolve.extensions,
      modules: ['node_modules'],
    };

    config.module.rules = [
      ...deckWebpackConfig.module.rules.map((rule) => {
        // There are some issues with thread-loader and storybook, so disabling it until it gets fixed.
        rule.use = (rule.use || []).filter(({ loader }) => loader !== 'thread-loader');
        return rule;
      }),
      {
        test: /\.mdx?$/,
        use: [
          { loader: 'babel-loader', options: { presets: ['@babel/preset-env', '@babel/preset-react'] } },
          { loader: '@mdx-js/loader', options: { compilers: [createCompiler()] } },
        ],
      },
      {
        test: /\.stories\.tsx?$/,
        loaders: [
          {
            loader: require.resolve('@storybook/source-loader'),
            options: { parser: 'typescript' },
          },
        ],
        enforce: 'pre',
      },
    ];

    config.watch = true;

    return config;
  },
};

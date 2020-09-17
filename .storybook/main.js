const deckWebpackConfigurer = require('../webpack.config');

module.exports = {
  addons: ['@storybook/preset-typescript'],
  stories: ['../app/scripts/modules/core/src/presentation/**/*.stories.[tj]sx'],
  webpackFinal: async config => {
    const deckWebpackConfig = deckWebpackConfigurer({}, {});

    config.resolve = {
      ...config.resolve,
      alias: deckWebpackConfig.resolve.alias,
      extensions: deckWebpackConfig.resolve.extensions,
      modules: ['node_modules'],
    };

    config.module.rules = deckWebpackConfig.module.rules.map(rule => {
      // There are some issues with thread-loader and storybook, so disabling it until it gets fixed.
      rule.use = (rule.use || []).filter(({ loader }) => loader !== 'thread-loader');
      return rule;
    });

    config.watch = true;

    return config;
  },
};

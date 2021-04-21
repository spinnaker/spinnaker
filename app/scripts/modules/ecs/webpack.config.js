const { getBaseConfig } = require('../base.webpack.config');
const baseConfig = getBaseConfig('ecs');

module.exports = {
  ...baseConfig,
  externals: [...baseConfig.externals, '@spinnaker/core', '@spinnaker/amazon', '@spinnaker/docker'],
};

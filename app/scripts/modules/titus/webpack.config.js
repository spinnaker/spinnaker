const { getBaseConfig } = require('../base.webpack.config');
const baseConfig = getBaseConfig('titus');

module.exports = {
  ...baseConfig,
  externals: [...baseConfig.externals, '@spinnaker/core', '@spinnaker/amazon', '@spinnaker/docker'],
};

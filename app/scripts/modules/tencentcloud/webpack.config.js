const { getBaseConfig } = require('../base.webpack.config');
const baseConfig = getBaseConfig('tencentcloud');

module.exports = {
  ...baseConfig,
  externals: [...baseConfig.externals, '@spinnaker/core'],
};

const { getBaseConfig } = require('../base.webpack.config');
const baseConfig = getBaseConfig('google');

module.exports = {
  ...baseConfig,
  externals: [...baseConfig.externals, '@spinnaker/core'],
};

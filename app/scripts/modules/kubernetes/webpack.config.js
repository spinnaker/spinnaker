const { getBaseConfig } = require('../base.webpack.config');
const baseConfig = getBaseConfig('kubernetes');

module.exports = {
  ...baseConfig,
  externals: [...baseConfig.externals, '@spinnaker/core'],
};

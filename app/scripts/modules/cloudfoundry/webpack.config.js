const { getBaseConfig } = require('../base.webpack.config');
const baseConfig = getBaseConfig('cloudfoundry');

module.exports = {
  ...baseConfig,
  externals: [...baseConfig.externals, '@spinnaker/core'],
};

const { getBaseConfig } = require('../base.webpack.config');
const baseConfig = getBaseConfig('docker');

module.exports = {
  ...baseConfig,
  externals: [...baseConfig.externals, '@spinnaker/core'],
};

const { getBaseConfig } = require('../base.webpack.config');
const baseConfig = getBaseConfig('azure');

module.exports = {
  ...baseConfig,
  externals: [...baseConfig.externals, '@spinnaker/core'],
};

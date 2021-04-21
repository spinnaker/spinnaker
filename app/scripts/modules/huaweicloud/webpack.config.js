const { getBaseConfig } = require('../base.webpack.config');
const baseConfig = getBaseConfig('huaweicloud');

module.exports = {
  ...baseConfig,
  externals: [...baseConfig.externals, '@spinnaker/core'],
};

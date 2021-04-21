const { getBaseConfig } = require('../base.webpack.config');
const baseConfig = getBaseConfig('appengine');

module.exports = {
  ...baseConfig,
  externals: [...baseConfig.externals, '@spinnaker/core'],
};

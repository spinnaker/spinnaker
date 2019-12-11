module.exports = {
  rules: {
    'component-literal': require('./rules/component-literal'),
    strictdi: require('./rules/strictdi'),
    'no-ng-module-export': require('./rules/no-ng-module-export'),
  },
  configs: {
    base: require('./base.config.js'),
  },
};

module.exports = {
  rules: {
    'component-literal': require('./rules/component-literal'),
    strictdi: require('./rules/strictdi'),
    'no-ng-module-export': require('./rules/no-ng-module-export'),
    'no-require-angularjs-deps': require('./rules/no-require-angularjs-deps'),
  },
  configs: {
    base: require('./base.config.js'),
  },
};

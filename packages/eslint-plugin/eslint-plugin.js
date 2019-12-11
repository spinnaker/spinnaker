module.exports = {
  rules: {
    'ng-no-component-class': require('./rules/ng-no-component-class'),
    'ng-no-module-export': require('./rules/ng-no-module-export'),
    'ng-no-require-angularjs': require('./rules/ng-no-require-angularjs'),
    'ng-no-require-module-deps': require('./rules/ng-no-require-module-deps'),
    'ng-strictdi': require('./rules/ng-strictdi'),
  },
  configs: {
    base: require('./base.config.js'),
  },
};

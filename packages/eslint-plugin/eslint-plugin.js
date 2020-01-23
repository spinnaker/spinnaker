module.exports = {
  rules: {
    'import-from-alias-not-npm': require('./rules/import-from-alias-not-npm'),
    'import-from-npm-not-alias': require('./rules/import-from-npm-not-alias'),
    'import-from-npm-not-relative': require('./rules/import-from-npm-not-relative'),
    'import-relative-within-subpackage': require('./rules/import-relative-within-subpackage'),
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

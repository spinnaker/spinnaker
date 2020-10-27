module.exports = {
  rules: {
    'api-no-slashes': require('./rules/api-no-slashes'),
    'import-from-alias-not-npm': require('./rules/import-from-alias-not-npm'),
    'import-from-npm-not-alias': require('./rules/import-from-npm-not-alias'),
    'import-from-npm-not-relative': require('./rules/import-from-npm-not-relative'),
    'import-relative-within-subpackage': require('./rules/import-relative-within-subpackage'),
    'ng-no-component-class': require('./rules/ng-no-component-class'),
    'ng-no-module-export': require('./rules/ng-no-module-export'),
    'ng-no-require-angularjs': require('./rules/ng-no-require-angularjs'),
    'ng-no-require-module-deps': require('./rules/ng-no-require-module-deps'),
    'ng-strictdi': require('./rules/ng-strictdi'),
    'prefer-promise-like': require('./rules/prefer-promise-like'),
    'react2angular-with-error-boundary.spec.js': require('./rules/react2angular-with-error-boundary'),
  },
  configs: {
    base: require('./base.config.js'),
    none: require('./none.config.js'),
  },
};

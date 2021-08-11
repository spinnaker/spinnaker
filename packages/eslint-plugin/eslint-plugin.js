// This registers Typescript compiler instance into node.js's require()
require('ts-node').register({ transpileOnly: true, skipProject: true });

module.exports = {
  rules: {
    'import-sort': require('./rules/import-sort'),
    'api-deprecation': require('./rules/api-deprecation'),
    'api-no-slashes': require('./rules/api-no-slashes'),
    'api-no-unused-chaining': require('./rules/api-no-unused-chaining'),
    'import-from-alias-not-npm': require('./rules/import-from-alias-not-npm'),
    'import-from-npm-not-alias': require('./rules/import-from-npm-not-alias'),
    'import-from-npm-not-relative': require('./rules/import-from-npm-not-relative'),
    'import-from-presentation-not-core': require('./rules/import-from-presentation-not-core'),
    'import-relative-within-subpackage': require('./rules/import-relative-within-subpackage'),
    'migrate-to-mock-http-client': require('./rules/migrate-to-mock-http-client'),
    'ng-no-component-class': require('./rules/ng-no-component-class'),
    'ng-no-module-export': require('./rules/ng-no-module-export'),
    'ng-no-require-angularjs': require('./rules/ng-no-require-angularjs'),
    'ng-no-require-module-deps': require('./rules/ng-no-require-module-deps'),
    'ng-strictdi': require('./rules/ng-strictdi'),
    'prefer-promise-like': require('./rules/prefer-promise-like'),
    'react2angular-with-error-boundary': require('./rules/react2angular-with-error-boundary'),
    'rest-prefer-static-strings-in-initializer': require('./rules/rest-prefer-static-strings-in-initializer'),
  },
  configs: {
    base: require('./base.config.js'),
    none: require('./none.config.js'),
  },
};

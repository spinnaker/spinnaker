import apiDeprecation from './rules/api-deprecation';
import apiNoSlashes from './rules/api-no-slashes';
import apiNoUnusedChaining from './rules/api-no-unused-chaining';
import importFromAliasNotNpm from './rules/import-from-alias-not-npm';
import importFromNpmNotAlias from './rules/import-from-npm-not-alias';
import importFromNpmNotRelative from './rules/import-from-npm-not-relative';
import importFromPresentationNotCore from './rules/import-from-presentation-not-core';
import importRelativeWithinSubpackage from './rules/import-relative-within-subpackage';
import importSort from './rules/import-sort';
import migrateToMockHttpClient from './rules/migrate-to-mock-http-client';
import ngNoComponentClass from './rules/ng-no-component-class';
import ngNoModuleExport from './rules/ng-no-module-export';
import ngNoRequireAngularJS from './rules/ng-no-require-angularjs';
import ngNoRequireModuleDeps from './rules/ng-no-require-module-deps';
import ngStrictDI from './rules/ng-strictdi';
import preferPromiseLike from './rules/prefer-promise-like';
import react2angularWithErrorBoundary from './rules/react2angular-with-error-boundary';
import restPreferStaticStringsInInitializer from './rules/rest-prefer-static-strings-in-initializer';

const plugin = {
  configs: {
    base: require('./base.config.js'),
    none: require('./none.config.js'),
  },
  rules: {
    'api-deprecation': apiDeprecation,
    'api-no-slashes': apiNoSlashes,
    'api-no-unused-chaining': apiNoUnusedChaining,
    'import-from-alias-not-npm': importFromAliasNotNpm,
    'import-from-npm-not-alias': importFromNpmNotAlias,
    'import-from-npm-not-relative': importFromNpmNotRelative,
    'import-from-presentation-not-core': importFromPresentationNotCore,
    'import-relative-within-subpackage': importRelativeWithinSubpackage,
    'import-sort': importSort,
    'migrate-to-mock-http-client': migrateToMockHttpClient,
    'ng-no-component-class': ngNoComponentClass,
    'ng-no-module-export': ngNoModuleExport,
    'ng-no-require-angularjs': ngNoRequireAngularJS,
    'ng-no-require-module-deps': ngNoRequireModuleDeps,
    'ng-strictdi': ngStrictDI,
    'prefer-promise-like': preferPromiseLike,
    'react2angular-with-error-boundary': react2angularWithErrorBoundary,
    'rest-prefer-static-strings-in-initializer': restPreferStaticStringsInInitializer,
  },
};

export default plugin;

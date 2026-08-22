import apiDeprecation from './rules/api-deprecation';
import apiNoSlashes from './rules/api-no-slashes';
import apiNoUnusedChaining from './rules/api-no-unused-chaining';
import importFromAliasNotNpm from './rules/import-from-alias-not-npm';
import importFromNpmNotAlias from './rules/import-from-npm-not-alias';
import importFromNpmNotRelative from './rules/import-from-npm-not-relative';
import importFromPresentationNotCore from './rules/import-from-presentation-not-core';
import importRelativeWithinSubpackage from './rules/import-relative-within-subpackage';
import importSort from './rules/import-sort';
import restPreferStaticStringsInInitializer from './rules/rest-prefer-static-strings-in-initializer';

const rules = {
  'api-deprecation': apiDeprecation,
  'api-no-slashes': apiNoSlashes,
  'api-no-unused-chaining': apiNoUnusedChaining,
  'import-from-alias-not-npm': importFromAliasNotNpm,
  'import-from-npm-not-alias': importFromNpmNotAlias,
  'import-from-npm-not-relative': importFromNpmNotRelative,
  'import-from-presentation-not-core': importFromPresentationNotCore,
  'import-relative-within-subpackage': importRelativeWithinSubpackage,
  'import-sort': importSort,
  'rest-prefer-static-strings-in-initializer': restPreferStaticStringsInInitializer,
};

const plugin = {
  rules,
  // Configs are loaded lazily to avoid circular dependency
  get configs() {
    return {
      base: require('./base.config.js'),
      'legacy-base': require('./legacy.config.js'),
      none: require('./none.config.js'),
    };
  },
};

export default plugin;

const { globalIgnores } = require('eslint/config');

const tsParser = require('@typescript-eslint/parser');
const typescriptEslint = require('@typescript-eslint/eslint-plugin');
const reactHooks = require('eslint-plugin-react-hooks');

// Import rules directly to avoid circular dependency when this config is bundled in the plugin
// Use a function to get rules lazily
const getSpinnakerRules = () => require('./index.js').rules;

const globals = require('globals');
const js = require('@eslint/js');

const { FlatCompat } = require('@eslint/eslintrc');

const compat = new FlatCompat({
  baseDirectory: __dirname,
  recommendedConfig: js.configs.recommended,
  allConfig: js.configs.all,
});

module.exports = [
  js.configs.recommended,
  ...compat.extends('prettier', 'plugin:@typescript-eslint/recommended'),
  {
    languageOptions: {
      parser: tsParser,
      sourceType: 'module',
      parserOptions: {},

      globals: {
        ...globals.browser,
        ...globals.node,
        ...globals.jasmine,
        angular: true,
        $: true,
        _: true,
      },
    },

    plugins: {
      '@typescript-eslint': typescriptEslint,
      '@spinnaker': { rules: getSpinnakerRules() },
      'react-hooks': { rules: reactHooks.rules },
    },

    rules: {
      '@spinnaker/import-sort': 1,
      '@spinnaker/api-deprecation': 2,
      '@spinnaker/api-no-slashes': 2,
      '@spinnaker/api-no-unused-chaining': 2,
      '@spinnaker/import-from-alias-not-npm': 2,
      '@spinnaker/import-from-npm-not-alias': 2,
      '@spinnaker/import-from-npm-not-relative': 2,
      '@spinnaker/import-from-presentation-not-core': 2,
      '@spinnaker/import-relative-within-subpackage': 2,
      '@spinnaker/migrate-to-mock-http-client': 2,
      '@spinnaker/ng-no-component-class': 2,
      '@spinnaker/ng-no-module-export': 2,
      '@spinnaker/ng-no-require-angularjs': 2,
      '@spinnaker/ng-no-require-module-deps': 2,
      '@spinnaker/ng-strictdi': 'off',
      '@spinnaker/prefer-promise-like': 1,
      '@spinnaker/react2angular-with-error-boundary': 2,
      '@spinnaker/rest-prefer-static-strings-in-initializer': 2,
      indent: 'off',
      'member-ordering': 'off',
      'no-console': [
        'error',
        {
          allow: ['warn', 'error'],
        },
      ],
      'no-extra-boolean-cast': 'off',
      'no-prototype-builtins': 'off',
      'one-var': [
        'error',
        {
          initialized: 'never',
        },
      ],
      'prefer-rest-params': 'off',
      'prefer-spread': 'off',
      'require-atomic-updates': 'off',
      'react-hooks/rules-of-hooks': 'error',
      '@typescript-eslint/array-type': [
        'error',
        {
          default: 'array-simple',
        },
      ],
      '@typescript-eslint/ban-ts-ignore': 'off',
      '@typescript-eslint/consistent-type-imports': [
        'error',
        {
          prefer: 'type-imports',
        },
      ],
      '@typescript-eslint/explicit-function-return-type': 'off',
      '@typescript-eslint/explicit-member-accessibility': 'off',
      '@typescript-eslint/indent': 'off',
      '@typescript-eslint/interface-name-prefix': 'off',
      '@typescript-eslint/no-case-declarations': 'off',
      '@typescript-eslint/no-empty-function': 'off',
      '@typescript-eslint/no-empty-interface': 'off',
      '@typescript-eslint/no-explicit-any': 'off',
      '@typescript-eslint/no-object-literal-type-assertion': 'off',
      '@typescript-eslint/no-parameter-properties': 'off',
      '@typescript-eslint/no-this-alias': 'off',
      '@typescript-eslint/no-triple-slash-reference': 'off',
      '@typescript-eslint/no-unused-vars': 'off',
      '@typescript-eslint/no-use-before-define': 'off',
      '@typescript-eslint/no-var-requires': 'off',
      '@typescript-eslint/triple-slash-reference': 'off',
      '@typescript-eslint/explicit-module-boundary-types': 'off',
      '@typescript-eslint/ban-types': 'off',
      '@typescript-eslint/ban-ts-comment': 'off',
      '@typescript-eslint/no-require-imports': 'off',
      '@typescript-eslint/no-unused-expressions': 'off',
      '@typescript-eslint/no-empty-object-type': 'off',
      '@typescript-eslint/no-unsafe-function-type': 'off',
    },
  },
  {
    files: ['**/*.js', '**/*.jsx'],
    rules: {
      '@typescript-eslint/no-use-before-define': 'off',
    },
  },
  {
    files: ['**/*.ts', '**/*.tsx'],
    rules: {
      'no-undef': 'off',
    },
  },
  globalIgnores(['**/*.spec.*', './template/**/*']),
];

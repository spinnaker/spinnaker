const { globalIgnores } = require('eslint/config');

const tsParser = require('@typescript-eslint/parser');
const typescriptEslint = require('@typescript-eslint/eslint-plugin');

// Import rules directly to avoid circular dependency when this config is bundled in the plugin
// Use a function to get rules lazily
const getSpinnakerRules = () => require('./index.js').rules;
const globals = require('globals');

module.exports = [
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
    },

    rules: {},
  },
  globalIgnores(['**/*.spec.*', './template/**/*']),
];

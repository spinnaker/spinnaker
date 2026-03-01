const RuleTester = require('eslint').RuleTester;
module.exports = new RuleTester({
  languageOptions: {
    ecmaVersion: 2017,
    sourceType: 'module',
    parser: require('@typescript-eslint/parser'),
  },
});

const RuleTester = require('eslint').RuleTester;
module.exports = new RuleTester({
  parserOptions: {
    ecmaVersion: 8,
    sourceType: 'module',
  },
  parser: require.resolve('@typescript-eslint/parser'),
});

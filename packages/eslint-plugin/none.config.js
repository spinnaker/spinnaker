module.exports = {
  parser: '@typescript-eslint/parser',
  parserOptions: { sourceType: 'module' },
  plugins: ['@typescript-eslint', '@spinnaker/eslint-plugin'],
  extends: [],
  rules: {},
  env: {
    browser: true,
    node: true,
    es6: true,
    jasmine: true,
  },
  globals: {
    angular: true,
    $: true,
    _: true,
  },
};

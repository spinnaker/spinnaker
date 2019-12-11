module.exports = {
  parser: '@typescript-eslint/parser',
  plugins: ['@typescript-eslint', '@spinnaker/eslint-plugin'],
  extends: ['eslint:recommended', 'prettier', 'prettier/@typescript-eslint', 'plugin:@typescript-eslint/recommended'],
  rules: {},
  overrides: [
    {
      files: ['*.js', '*.jsx'],
      rules: {
        '@typescript-eslint/no-use-before-define': 'off',
      },
    },
    {
      files: ['*.ts', '*.tsx'],
      rules: {
        'no-undef': 'off', // typescript already checks this
      },
    },
  ],
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

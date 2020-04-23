const { checkPackageJson } = require('./lint.package.json');
const { checkTsconfig } = require('./lint.tsconfig.json');
const { checkRollupConfig } = require('./lint.rollup.config');
const { checkPrettierRc } = require('./lint.prettierrc');
const { checkEslintRc } = require('./lint.eslintrc');

const linters = [checkPackageJson, checkTsconfig, checkRollupConfig, checkPrettierRc, checkEslintRc];
module.exports = { linters };

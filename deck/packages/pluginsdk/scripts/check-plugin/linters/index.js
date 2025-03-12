const { checkEslintRc } = require('./lint.eslintrc');
const { checkLintStagedRc } = require('./lint.lintstagedrc.json');
const { checkPackageJson } = require('./lint.package.json');
const { checkPrettierRc } = require('./lint.prettierrc');
const { checkRollupConfig } = require('./lint.rollup.config');
const { checkTsconfig } = require('./lint.tsconfig.json');

const linters = [checkEslintRc, checkLintStagedRc, checkPackageJson, checkPrettierRc, checkRollupConfig, checkTsconfig];
module.exports = { linters };

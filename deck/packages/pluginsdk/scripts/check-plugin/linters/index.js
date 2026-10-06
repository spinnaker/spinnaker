const { checkEslintRc } = require('./lint.eslintrc');
const { checkLintStagedRc } = require('./lint.lintstagedrc.json');
const { checkPackageJson } = require('./lint.package.json');
const { checkPrettierRc } = require('./lint.prettierrc');
const { checkTsconfig } = require('./lint.tsconfig.json');
const { checkViteConfig } = require('./lint.vite.config');

const linters = [checkEslintRc, checkLintStagedRc, checkPackageJson, checkPrettierRc, checkViteConfig, checkTsconfig];
module.exports = { linters };

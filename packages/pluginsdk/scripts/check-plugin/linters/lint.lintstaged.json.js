const { assertFileExists } = require('../asserters/assertFileExists');

function checkLintStaged(report) {
  assertFileExists(report, '.lintstagedrc.json');
}

module.exports = { checkLintStaged };

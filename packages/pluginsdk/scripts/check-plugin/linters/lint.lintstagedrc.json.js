const { assertFileExists } = require('../asserters/assertFileExists');

function checkLintStagedRc(report) {
  assertFileExists(report, '.lintstagedrc.json');
}

module.exports = { checkLintStagedRc };

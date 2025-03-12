const { assertJavascriptFile } = require('../asserters/assertJavascriptFile');

function checkEslintRc(report) {
  assertJavascriptFile(
    report,
    '.eslintrc.js',
    '.eslintrc.js',
    'Eslint config',
    '@spinnaker/pluginsdk/pluginconfig/eslintrc',
  );
}

module.exports = { checkEslintRc };

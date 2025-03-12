const { assertJavascriptFile } = require('../asserters/assertJavascriptFile');

function checkPrettierRc(report) {
  assertJavascriptFile(
    report,
    '.prettierrc.js',
    'scaffold.prettierrc.js',
    'Prettier config',
    '@spinnaker/pluginsdk/pluginconfig/prettierrc.js',
  );
}

module.exports = { checkPrettierRc };

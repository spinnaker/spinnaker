const { assertJavascriptFile } = require('../asserters/assertJavascriptFile');

function checkRollupConfig(report) {
  assertJavascriptFile(
    report,
    'rollup.config.js',
    'rollup.config.js',
    'Rollup config',
    '@spinnaker/pluginsdk/pluginconfig/rollup.config',
  );
}

module.exports = { checkRollupConfig };

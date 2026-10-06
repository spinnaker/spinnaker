const { assertJavascriptFile } = require('../asserters/assertJavascriptFile');

function checkViteConfig(report) {
  assertJavascriptFile(
    report,
    'vite.config.js',
    'vite.config.js',
    'Vite config',
    '@spinnaker/pluginsdk/pluginconfig/vite.config',
  );
}

module.exports = { checkViteConfig };

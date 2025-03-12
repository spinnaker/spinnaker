const { assertJsonFile } = require('../asserters/assertJsonFile');
const { assertFileExists } = require('../asserters/assertFileExists');
const { readJson } = require('@spinnaker/scripts/read-write-json');

function checkTsconfig(report) {
  const exists = assertFileExists(report, 'tsconfig.json');

  if (exists) {
    const tsConfigJson = readJson('tsconfig.json');

    const checkTsconfigField = assertJsonFile(report, 'tsconfig.json', tsConfigJson);

    checkTsconfigField('extends', '@spinnaker/pluginsdk/pluginconfig/tsconfig.json');
    checkTsconfigField('compilerOptions.outDir', 'build/dist');
    checkTsconfigField('compilerOptions.rootDir', 'src');
  }
}

module.exports = { checkTsconfig };

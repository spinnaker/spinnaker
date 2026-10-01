const fs = require('node:fs');
const commentJson = require('comment-json');
const { assertJsonFile } = require('../asserters/assertJsonFile');
const { assertFileExists } = require('../asserters/assertFileExists');

function checkTsconfig(report) {
  const exists = assertFileExists(report, 'tsconfig.json');

  if (exists) {
    const tsConfigJson = commentJson.parse(fs.readFileSync('tsconfig.json', 'utf8'));

    const checkTsconfigField = assertJsonFile(report, 'tsconfig.json', tsConfigJson);

    checkTsconfigField('extends', '@spinnaker/pluginsdk/pluginconfig/tsconfig.json');
    checkTsconfigField('compilerOptions.outDir', 'build/dist');
    checkTsconfigField('compilerOptions.rootDir', 'src');
  }
}

module.exports = { checkTsconfig };

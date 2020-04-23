const fs = require('fs');
const path = require('path');
const { assertFileExists } = require('./assertFileExists');
const { restoreFile } = require('../util/restoreFile');

function assertJavascriptFile(report, filename, pristineFilename, name, requireString) {
  const exists = assertFileExists(report, filename, pristineFilename);

  if (exists) {
    // packages/pluginsdk/scaffold/${pristineFilename}
    const pristinePath = path.resolve(__dirname, '..', '..', '..', 'scaffold', pristineFilename);
    const pristineFile = fs.readFileSync(pristinePath).toString();
    const pluginFile = fs.readFileSync(filename).toString();
    const hasRequire = pluginFile.includes(`require('${requireString}')`);
    const resolution = `restore ${filename} from scaffold defaults`;
    const restore = () => restoreFile(filename, pristineFilename);
    report(`${name} extends @spinnaker/pluginsdk`, hasRequire, `--fix: ${resolution}`, restore);
    report(
      filename,
      pristineFile.trim() === pluginFile.trim() || null,
      `--fix-warnings: ${resolution} (${filename} has been customized)`,
      restore,
    );
  }
}

module.exports = { assertJavascriptFile };

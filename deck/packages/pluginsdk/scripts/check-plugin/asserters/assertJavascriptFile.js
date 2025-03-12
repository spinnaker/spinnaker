const fs = require('fs');
const path = require('path');
const pluginsdkdir = require('../../pluginsdkdir');
const { assertFileExists } = require('./assertFileExists');

function assertJavascriptFile(report, filename, pristineFilename, name, requireString) {
  const exists = assertFileExists(report, filename, pristineFilename);

  if (exists) {
    // packages/pluginsdk/scaffold/${pristineFilename}
    const pristinePath = path.resolve(pluginsdkdir, 'scaffold', pristineFilename);
    const pristineFile = fs.readFileSync(pristinePath).toString();
    const pluginFile = fs.readFileSync(filename).toString();
    const hasRequire = pluginFile.includes(requireString);
    const resolution = {
      description: `Restore ${filename} from scaffold defaults (this will revert any local changes)`,
      command: `npx restore-scaffold-file ${filename}`,
    };
    report(`${filename} does not extend ${requireString}`, hasRequire, resolution);
    report(
      `${filename} extends ${requireString} but has been customized`,
      hasRequire && pristineFile.trim() !== pluginFile.trim() ? null : true,
      resolution,
    );
  }
}

module.exports = { assertJavascriptFile };

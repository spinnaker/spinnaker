const fs = require('fs');
const { restoreFile } = require('../util/restoreFile');

const assertFileExists = (report, filename, pristineFilename = filename) => {
  const exists = fs.existsSync(filename);
  const resolution = `restore ${filename} from scaffold defaults`;
  const restore = () => restoreFile(filename, pristineFilename);
  report(`${filename} exists`, exists, resolution, restore);
  return exists;
};

module.exports = { assertFileExists };

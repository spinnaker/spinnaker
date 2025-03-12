const fs = require('fs');

const assertFileExists = (report, filename, pristineFilename = filename) => {
  const ok = fs.existsSync(filename);
  const resolution = {
    description: `restore ${filename} from scaffold defaults`,
    command: `npx restore-scaffold-file ${filename}`,
  };
  report(`${filename} does not exist`, ok, resolution);
  return ok;
};

module.exports = { assertFileExists };

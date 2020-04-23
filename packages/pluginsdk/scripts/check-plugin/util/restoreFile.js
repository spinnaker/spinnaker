const fs = require('fs');
const path = require('path');

function restoreFile(file, pristineFilename = file) {
  const srcFile = path.resolve(__dirname, '..', '..', '..', 'scaffold', pristineFilename);
  fs.writeFileSync(file, fs.readFileSync(srcFile));
  console.log(`fixed: restored ${file} to default from scaffold`);
}

module.exports = { restoreFile };

const path = require('node:path');

const packageJson = require('./package.json');
const { createPackageExternal, createViteLibraryConfig } = require('@spinnaker/scripts/config/vite.config.base.module');

module.exports = createViteLibraryConfig({
  root: __dirname,
  packageJson,
  external: createPackageExternal([packageJson.dependencies, packageJson.peerDependencies], ['@spinnaker/core']),
  outDir: path.join(__dirname, 'dist'),
});

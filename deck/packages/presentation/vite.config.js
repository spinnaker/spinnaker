import { createRequire } from 'node:module';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const require = createRequire(import.meta.url);
const packageJson = require('./package.json');
const { createPackageExternal, createViteLibraryConfig } = require('@spinnaker/scripts/config/vite.config.base.module');
const root = path.dirname(fileURLToPath(import.meta.url));

export default createViteLibraryConfig({
  root,
  packageJson,
  external: createPackageExternal(packageJson.peerDependencies),
});

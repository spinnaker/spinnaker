import fs from 'node:fs';
import { createRequire } from 'node:module';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const require = createRequire(import.meta.url);
const packageJson = require('./package.json');
const { createPackageExternal, createViteLibraryConfig } = require('@spinnaker/scripts/config/vite.config.base.module');
const root = path.dirname(fileURLToPath(import.meta.url));
const requiredAmbientTypes = ['index.d.ts', 'png.d.ts', 'svg.d.ts'];
const ambientReference = /^\/\/\/\s*<reference\s+(?:path=["'](?:\.\.\/src\/types\/index\.d\.ts|\.\/types\/index\.d\.ts)["']|types=["'](?:\.\/)?types["'])\s*\/>\s*$/m;

async function finalizeDeclarations(resolvedConfig) {
  const packageRoot = resolvedConfig.root;
  const sourceTypes = path.join(packageRoot, 'src/types');
  const outputRoot = path.resolve(packageRoot, resolvedConfig.build.outDir);
  const outputTypes = path.join(outputRoot, 'types');
  const declarationPath = path.join(outputRoot, 'index.d.ts');
  const declaration = await fs.promises.readFile(declarationPath, 'utf8');

  await Promise.all(requiredAmbientTypes.map((file) => fs.promises.access(path.join(sourceTypes, file))));
  if (!ambientReference.test(declaration)) {
    throw new Error(`Unrecognized or missing Core declaration reference in ${declarationPath}`);
  }

  await fs.promises.rm(outputTypes, { force: true, recursive: true });
  await fs.promises.cp(sourceTypes, outputTypes, { recursive: true });
  await fs.promises.writeFile(
    declarationPath,
    declaration.replace(ambientReference, '/// <reference types="./types" />'),
  );
}

const packageExternal = createPackageExternal(packageJson.dependencies);
const exactExternals = new Set(['root/version', 'root/version.json']);

export default createViteLibraryConfig({
  root,
  packageJson,
  external: (id) => packageExternal(id) || exactExternals.has(id),
  afterDeclarations: finalizeDeclarations,
}).then((config) => {
  config.css.preprocessorMaxWorkers = 0;
  return config;
});

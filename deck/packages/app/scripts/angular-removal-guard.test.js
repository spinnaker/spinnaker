const assert = require('node:assert/strict');
const { readdirSync, readFileSync } = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const workspaceRoot = path.resolve(__dirname, '../../..');
const coreSourceRoot = path.resolve(__dirname, '../../core/src');
const angularServicesPath = path.join(coreSourceRoot, 'angular/services.ts');
const bridgePath = path.join(coreSourceRoot, 'navigation/legacyStateConfig.bridge.ts');
const legacyImportPackage = ['ng', 'import'].join('');
const routeProvider = /['"](?:stateConfigProvider|applicationStateProvider)['"]/;

function productionSourceFiles(directory) {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const entryPath = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      return productionSourceFiles(entryPath);
    }
    if (!/\.(?:js|jsx|ts|tsx)$/.test(entry.name) || /\.(?:spec|test)\.[^.]+$/.test(entry.name)) {
      return [];
    }
    return [entryPath];
  });
}

function angularConfigCallbacks(source) {
  return source.match(/\.config\s*\(\s*\[[\s\S]*?\]\s*\)\s*;/g) || [];
}

function workspaceSourceFiles(directory) {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    if (['.cache-loader', 'build', 'dist', 'node_modules'].includes(entry.name)) {
      return [];
    }

    const entryPath = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      return workspaceSourceFiles(entryPath);
    }
    return /\.(?:js|jsx|json|ts|tsx)$/.test(entry.name) ? [entryPath] : [];
  });
}

test('Core routes do not depend on the legacy Angular state config bridge', () => {
  assert.throws(() => readFileSync(bridgePath, 'utf8'), { code: 'ENOENT' });

  const bridgeReferences = productionSourceFiles(coreSourceRoot).filter((file) =>
    readFileSync(file, 'utf8').includes('legacyStateConfig.bridge'),
  );
  assert.deepEqual(bridgeReferences, []);
});

test('production Core source has no Angular route config callbacks', () => {
  const routeConfigs = productionSourceFiles(coreSourceRoot).flatMap((file) =>
    angularConfigCallbacks(readFileSync(file, 'utf8'))
      .filter((callback) => routeProvider.test(callback))
      .map(() => path.relative(coreSourceRoot, file)),
  );

  assert.deepEqual(routeConfigs, []);
});

test('direct bootstrap explicitly loads Core routes', () => {
  const bootstrapSource = readFileSync(path.join(coreSourceRoot, 'bootstrap/bootstrapDeck.tsx'), 'utf8');

  assert.match(bootstrapSource, /import ['"]\.\.\/navigation\/coreRoutes['"];?/);
});

test('AngularServices does not depend on the Angular global injector', () => {
  const source = readFileSync(angularServicesPath, 'utf8');

  assert.doesNotMatch(source, new RegExp(`from ['"]${legacyImportPackage}['"]`));
  assert.doesNotMatch(source, /\$injector/);
});

test('workspace source and dependency metadata do not reference the legacy Angular import bridge', () => {
  const references = workspaceSourceFiles(workspaceRoot)
    .filter((file) => readFileSync(file, 'utf8').includes(legacyImportPackage))
    .map((file) => path.relative(workspaceRoot, file));

  assert.deepEqual(references, []);
});

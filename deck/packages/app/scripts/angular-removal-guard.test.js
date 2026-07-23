const assert = require('node:assert/strict');
const { readdirSync, readFileSync } = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const coreSourceRoot = path.resolve(__dirname, '../../core/src');
const bridgePath = path.join(coreSourceRoot, 'navigation/legacyStateConfig.bridge.ts');
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

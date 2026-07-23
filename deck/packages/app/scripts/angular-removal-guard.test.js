const assert = require('node:assert/strict');
const { readdirSync, readFileSync } = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const deckRoot = path.resolve(__dirname, '../../..');
const repositoryRoot = path.resolve(deckRoot, '..');
const coreSourceRoot = path.resolve(__dirname, '../../core/src');
const routerConsumerRoots = [deckRoot, path.join(repositoryRoot, 'deck-kayenta/src')];
const angularServicesPath = path.join(coreSourceRoot, 'angular/services.ts');
const bridgePath = path.join(coreSourceRoot, 'navigation/legacyStateConfig.bridge.ts');
const legacyImportPackage = ['ng', 'import'].join('');
const routeProvider = /['"](?:stateConfigProvider|applicationStateProvider)['"]/;
const routerFacadeMembers = ['$' + 'state', '$' + 'stateParams', '$' + 'uiRouter', 'state' + 'Events', 'h' + 'as'];
const runtimeServiceFacadeMembers = [
  'cacheInitializer',
  'clusterService',
  'executionDetailsSectionService',
  'executionService',
  'infrastructureSearchService',
  'instanceTypeService',
  'loadBalancerReader',
  'pageTitleService',
  'providerServiceDelegate',
  'securityGroupReader',
  'serverGroupCommandBuilder',
  'serverGroupTransformer',
  'serverGroupWriter',
];
const angularServicesName = 'Angular' + 'Services';

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

function usesAngularServicesMember(source, member) {
  if (source.includes(`${angularServicesName}.${member}`)) {
    return true;
  }

  const escapedMember = member.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const indirectPropertyAccess = new RegExp(
    `${angularServicesName}\\s*\\[\\s*['"]${escapedMember}['"]\\s*\\]|` +
      `spyOnProperty\\(\\s*${angularServicesName}\\s*,\\s*['"]${escapedMember}['"]|` +
      `\\(\\s*${angularServicesName}\\s+as\\s+[^)]+\\)\\s*(?:\\.${escapedMember}|` +
      `\\[\\s*['"]${escapedMember}['"]\\s*\\])`,
  );
  if (indirectPropertyAccess.test(source)) {
    return true;
  }
  const memberBinding = new RegExp(`(?:^|,)\\s*${escapedMember}(?:\\s*:|\\s*(?:,|$))`);
  const destructuring = new RegExp(`(?:const|let|var)\\s*\\{([^{}]*)\\}\\s*=\\s*${angularServicesName}\\b`, 'g');
  return Array.from(source.matchAll(destructuring), (match) => match[1]).some((bindings) =>
    memberBinding.test(bindings),
  );
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

test('AngularServices does not expose router facade members', () => {
  const source = readFileSync(angularServicesPath, 'utf8');

  routerFacadeMembers.forEach((member) => {
    const escapedMember = member.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    assert.doesNotMatch(source, new RegExp(`(?:get\\s+)?${escapedMember}\\s*(?:\\(|\\{|:)`));
  });
});

test('workspace source and dependency metadata do not reference the legacy Angular import bridge', () => {
  const references = workspaceSourceFiles(deckRoot)
    .filter((file) => readFileSync(file, 'utf8').includes(legacyImportPackage))
    .map((file) => path.relative(deckRoot, file));

  assert.deepEqual(references, []);
});

test('Deck and Deck-Kayenta source and tests do not use AngularServices router facade members', () => {
  const references = routerConsumerRoots
    .flatMap((root) => workspaceSourceFiles(root))
    .flatMap((file) => {
      const source = readFileSync(file, 'utf8');
      return routerFacadeMembers
        .filter((member) => usesAngularServicesMember(source, member))
        .map((member) => `${path.relative(repositoryRoot, file)}: ${member}`);
    });

  assert.deepEqual(references, []);
});

test('Deck and Deck-Kayenta source and tests do not use AngularServices runtime service facade members', () => {
  const references = routerConsumerRoots
    .flatMap((root) => workspaceSourceFiles(root))
    .flatMap((file) => {
      const source = readFileSync(file, 'utf8');
      return runtimeServiceFacadeMembers
        .filter((member) => usesAngularServicesMember(source, member))
        .map((member) => `${path.relative(repositoryRoot, file)}: ${member}`);
    });

  assert.deepEqual(references, []);
});

test('router facade scan includes Deck functional tests', () => {
  const scannedFiles = routerConsumerRoots
    .flatMap((root) => workspaceSourceFiles(root))
    .map((file) => path.relative(deckRoot, file));

  assert.ok(scannedFiles.includes('test/functional/cypress/integration/core/bootstrap.spec.js'));
});

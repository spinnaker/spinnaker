const assert = require('node:assert/strict');
const {
  lstatSync,
  mkdirSync,
  mkdtempSync,
  readdirSync,
  readFileSync,
  rmSync,
  symlinkSync,
  writeFileSync,
} = require('node:fs');
const { tmpdir } = require('node:os');
const path = require('node:path');
const test = require('node:test');

const deckRoot = path.resolve(__dirname, '../../..');
const repositoryRoot = path.resolve(deckRoot, '..');
const coreSourceRoot = path.resolve(__dirname, '../../core/src');
const legacyFacadeConsumerRoots = [
  path.join(deckRoot, 'packages'),
  path.join(deckRoot, 'test'),
  path.join(repositoryRoot, 'deck-kayenta/src'),
];
const angularServicesPath = path.join(coreSourceRoot, 'angular', 'services.ts');
const angularServicesSpecPath = path.join(coreSourceRoot, 'angular', 'services.spec.ts');
const bridgePath = path.join(coreSourceRoot, 'navigation/legacyStateConfig.bridge.ts');
const legacyImportPackage = ['ng', 'import'].join('');
const routeProvider = /['"](?:stateConfigProvider|applicationStateProvider)['"]/;
const angularServicesName = 'Angular' + 'Services';
const angularServiceAccessorsName = 'Angular' + 'ServiceAccessors';
const angularServicesImportPath = ['angular', 'services'].join('/');
const sourceExtensions = ['.js', '.jsx', '.ts', '.tsx', '.mjs', '.cjs', '.mts', '.cts'];
const workspaceExtensions = new Set([...sourceExtensions, '.json']);
const generatedAndDependencyDirectories = new Set(['.cache-loader', 'build', 'dist', 'node_modules']);
const forbiddenFacadePatterns = [angularServicesName, angularServiceAccessorsName, angularServicesImportPath];

function productionSourceFiles(directory) {
  return workspaceSourceFiles(directory).filter(
    (file) => sourceExtensions.includes(path.extname(file)) && !/\.(?:spec|test)\.[^.]+$/.test(file),
  );
}

function angularConfigCallbacks(source) {
  return source.match(/\.config\s*\(\s*\[[\s\S]*?\]\s*\)\s*;/g) || [];
}

function workspaceSourceFiles(directory) {
  if (lstatSync(directory).isSymbolicLink()) {
    throw new Error(`Refusing to scan symbolic link: ${directory}`);
  }

  return readdirSync(directory, { withFileTypes: true })
    .sort((left, right) => left.name.localeCompare(right.name))
    .flatMap((entry) => {
      const entryPath = path.join(directory, entry.name);
      if (entry.isSymbolicLink()) {
        throw new Error(`Refusing to scan symbolic link: ${entryPath}`);
      }
      if (entry.isDirectory()) {
        if (generatedAndDependencyDirectories.has(entry.name)) {
          return [];
        }
        return workspaceSourceFiles(entryPath);
      }
      return workspaceExtensions.has(path.extname(entry.name)) ? [entryPath] : [];
    });
}

function findLegacyFacadeReferences(files, displayRoot) {
  return files.flatMap((file) => {
    const source = readFileSync(file, 'utf8');
    const relativePath = path.relative(displayRoot, file);
    return forbiddenFacadePatterns
      .filter((pattern) => source.includes(pattern))
      .map((pattern) => `${relativePath}: ${pattern}`);
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

test('the legacy Angular service facade implementation is absent', () => {
  assert.throws(() => readFileSync(angularServicesPath, 'utf8'), { code: 'ENOENT' });
});

test('the legacy Angular service facade spec is absent', () => {
  assert.throws(() => readFileSync(angularServicesSpecPath, 'utf8'), { code: 'ENOENT' });
});

test('workspace source and dependency metadata do not reference the legacy Angular import bridge', () => {
  const references = workspaceSourceFiles(deckRoot)
    .filter((file) => readFileSync(file, 'utf8').includes(legacyImportPackage))
    .map((file) => path.relative(deckRoot, file));

  assert.deepEqual(references, []);
});

test('Deck and Deck-Kayenta source and tests do not use legacy Angular service facades', () => {
  const references = findLegacyFacadeReferences(
    legacyFacadeConsumerRoots.flatMap((root) => workspaceSourceFiles(root)),
    repositoryRoot,
  );

  assert.deepEqual(references, []);
});

function withFixtureRoot(assertions) {
  const fixtureRoot = mkdtempSync(path.join(tmpdir(), 'angular-removal-guard-'));
  try {
    assertions(fixtureRoot);
  } finally {
    rmSync(fixtureRoot, { recursive: true, force: true });
  }
}

test('legacy facade scan includes and accepts its own source', () => {
  const scannedFiles = workspaceSourceFiles(path.dirname(__filename));

  assert.ok(scannedFiles.includes(__filename));
  assert.deepEqual(findLegacyFacadeReferences([__filename], repositoryRoot), []);
});

test('legacy facade scan detects names and raw paths independent of import formatting', () => {
  withFixtureRoot((fixtureRoot) => {
    const relativeImportPath = ['..', angularServicesImportPath].join('/');
    const fixtureSources = new Map([
      ['accessor.cts', angularServiceAccessorsName],
      ['formatted-require.cjs', ['const load = require', '  ', '(', '  `', relativeImportPath, '`', ');'].join('\n')],
      ['service.ts', angularServicesName],
      ['template-dynamic.mts', ['const load = import(`', relativeImportPath, '`);'].join('')],
    ]);
    fixtureSources.forEach((source, file) => writeFileSync(path.join(fixtureRoot, file), source));

    assert.deepEqual(findLegacyFacadeReferences(workspaceSourceFiles(fixtureRoot), fixtureRoot), [
      `accessor.cts: ${angularServiceAccessorsName}`,
      `formatted-require.cjs: ${angularServicesImportPath}`,
      `service.ts: ${angularServicesName}`,
      `template-dynamic.mts: ${angularServicesImportPath}`,
    ]);
  });
});

test('legacy facade scan checks every supported JavaScript and TypeScript extension', () => {
  withFixtureRoot((fixtureRoot) => {
    const fixtureFiles = sourceExtensions.map((extension) => `fixture${extension}`).sort();
    fixtureFiles.forEach((file) => writeFileSync(path.join(fixtureRoot, file), angularServicesName));

    assert.deepEqual(
      findLegacyFacadeReferences(workspaceSourceFiles(fixtureRoot), fixtureRoot),
      fixtureFiles.map((file) => `${file}: ${angularServicesName}`),
    );
  });
});

test('workspace source scan rejects file and directory symbolic links', () => {
  withFixtureRoot((fixtureRoot) => {
    const fileCaseRoot = path.join(fixtureRoot, 'file-case');
    mkdirSync(fileCaseRoot);
    const sourceFile = path.join(fileCaseRoot, 'source.ts');
    const linkedFile = path.join(fileCaseRoot, 'linked.ts');
    writeFileSync(sourceFile, 'export {};');
    symlinkSync(sourceFile, linkedFile, 'file');

    assert.throws(() => workspaceSourceFiles(fileCaseRoot), {
      message: `Refusing to scan symbolic link: ${linkedFile}`,
    });

    const directoryCaseRoot = path.join(fixtureRoot, 'directory-case');
    const sourceDirectory = path.join(directoryCaseRoot, 'source');
    const linkedDirectory = path.join(directoryCaseRoot, 'linked-source');
    mkdirSync(sourceDirectory, { recursive: true });
    writeFileSync(path.join(sourceDirectory, 'source.ts'), 'export {};');
    symlinkSync(sourceDirectory, linkedDirectory, 'dir');

    assert.throws(() => workspaceSourceFiles(directoryCaseRoot), {
      message: `Refusing to scan symbolic link: ${linkedDirectory}`,
    });
    assert.throws(() => workspaceSourceFiles(linkedDirectory), {
      message: `Refusing to scan symbolic link: ${linkedDirectory}`,
    });
  });
});

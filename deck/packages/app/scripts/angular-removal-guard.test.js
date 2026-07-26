const assert = require('node:assert/strict');
const {
  existsSync,
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
const angularRegistrationMethods = [
  'component',
  'config',
  'constant',
  'controller',
  'decorator',
  'directive',
  'factory',
  'filter',
  'provider',
  'run',
  'service',
  'value',
];
const angularRuntimePackages = [
  '@uirouter/angularjs',
  '@uirouter/react-hybrid',
  'angular',
  'angular-messages',
  'angular-sanitize',
  'angular-spinner',
  'angular-ui-bootstrap',
  'angulartics',
  'angulartics-google-analytics',
  'ui-select',
];
const workspaceExtensions = new Set([...sourceExtensions, '.html', '.json']);
const generatedAndDependencyDirectories = new Set(['.cache-loader', 'build', 'dist', 'node_modules']);
const forbiddenFacadePatterns = [angularServicesName, angularServiceAccessorsName, angularServicesImportPath];

function productionSourceFiles(directory) {
  return workspaceSourceFiles(directory).filter(
    (file) => sourceExtensions.includes(path.extname(file)) && !/\.(?:spec|test)\.[^.]+$/.test(file),
  );
}

function angularImportStatements(source) {
  return source.match(/(?:^|\n)\s*import[\s\S]*?;(?=\s*(?:\n|$))/g) || [];
}

function angularRuntimeImportStatements(source) {
  const imports = angularImportStatements(source).filter((statement) => {
    const importsAngular = angularRuntimePackages.some(
      (packageName) =>
        statement.includes(`from '${packageName}'`) ||
        statement.includes(`from "${packageName}"`) ||
        statement.includes(`import '${packageName}'`) ||
        statement.includes(`import "${packageName}"`),
    );
    const typeOnly = /^\s*import\s+type\b/.test(statement.trim());
    return importsAngular && !typeOnly;
  });
  const requires = angularRuntimePackages.flatMap((packageName) => {
    const escapedPackageName = packageName.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    return source.match(new RegExp(`require\\s*\\(\\s*['"]${escapedPackageName}['"]\\s*\\)`, 'g')) || [];
  });
  return imports.concat(requires);
}

function angularModuleFactoryPatterns(source) {
  const callableNames = new Set();
  const namespaceNames = new Set(['angular']);

  angularRuntimeImportStatements(source).forEach((statement) => {
    const namedModule = statement.match(/\bmodule\s*(?:as\s+([A-Za-z_$][\w$]*))?/);
    if (namedModule) callableNames.add(namedModule[1] || 'module');

    const namespace = statement.match(/import\s+\*\s+as\s+([A-Za-z_$][\w$]*)/);
    if (namespace) namespaceNames.add(namespace[1]);

    const defaultImport = statement.match(/import\s+([A-Za-z_$][\w$]*)\s+from\s+['"]angular['"]/);
    if (defaultImport) namespaceNames.add(defaultImport[1]);

    const requiredNamespace = source.match(
      /(?:const|let|var)\s+([A-Za-z_$][\w$]*)\s*=\s*require\s*\(\s*['"]angular['"]\s*\)/,
    );
    if (requiredNamespace) namespaceNames.add(requiredNamespace[1]);
  });

  return [
    ...Array.from(callableNames, (name) => new RegExp(`\\b${name}\\s*\\(`)),
    ...Array.from(namespaceNames, (name) => new RegExp(`\\b${name}\\s*\\.\\s*module\\s*\\(`)),
  ];
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

function findProductionAngularGraph(files, displayRoot) {
  return files.flatMap((file) => {
    const source = readFileSync(file, 'utf8');
    const relativePath = path.relative(displayRoot, file);
    const runtimeImports = angularRuntimeImportStatements(source);
    const findings = [];

    if (runtimeImports.length) findings.push(`${relativePath}: Angular runtime import`);
    if (angularModuleFactoryPatterns(source).some((pattern) => pattern.test(source))) {
      findings.push(`${relativePath}: Angular module factory`);
    }
    if (runtimeImports.length) {
      angularRegistrationMethods.forEach((method) => {
        if (new RegExp(`\\.${method}\\s*\\(`).test(source)) {
          findings.push(`${relativePath}: Angular .${method} registration`);
        }
      });
    }
    ['angularComponentFromReact', 'react2angular', 'angular2react'].forEach((adapter) => {
      if (source.includes(adapter)) findings.push(`${relativePath}: ${adapter}`);
    });

    return findings;
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

test('production Core source has no Angular runtime graph', () => {
  assert.deepEqual(findProductionAngularGraph(productionSourceFiles(coreSourceRoot), coreSourceRoot), []);
});

test('Core package root does not load the legacy Angular aggregate', () => {
  const coreIndex = readFileSync(path.join(coreSourceRoot, 'index.ts'), 'utf8');

  assert.doesNotMatch(coreIndex, /core\.module/);
});

test('Core and ECS source contain no Angular templates', () => {
  const ecsSourceRoot = path.resolve(__dirname, '../../ecs/src');
  const htmlFiles = [coreSourceRoot, ecsSourceRoot]
    .flatMap((root) => workspaceSourceFiles(root))
    .filter((file) => path.extname(file) === '.html')
    .map((file) => path.relative(deckRoot, file));

  assert.deepEqual(htmlFiles, []);
});

test('production package source has no Angular template requires', () => {
  const packagesRoot = path.join(deckRoot, 'packages');
  const packageSourceRoots = readdirSync(packagesRoot, { withFileTypes: true })
    .filter((entry) => entry.isDirectory() && entry.name !== 'eslint-plugin')
    .map((entry) => path.join(packagesRoot, entry.name, 'src'))
    .filter((sourceRoot) => existsSync(sourceRoot));
  const templateRequires = packageSourceRoots
    .flatMap((sourceRoot) => productionSourceFiles(sourceRoot))
    .filter((file) => /require\s*\([^)]*\.html['"]\s*\)/.test(readFileSync(file, 'utf8')))
    .map((file) => path.relative(deckRoot, file));

  assert.deepEqual(templateRequires, []);
});

test('legacy Angular root and React adapter infrastructure is absent', () => {
  assert.throws(() => readFileSync(path.join(coreSourceRoot, 'angular/angularComponentFromReact.tsx'), 'utf8'), {
    code: 'ENOENT',
  });
  assert.throws(() => readFileSync(path.join(coreSourceRoot, 'angular/angularComponentFromReact.spec.tsx'), 'utf8'), {
    code: 'ENOENT',
  });
  assert.throws(() => readFileSync(path.join(coreSourceRoot, 'bootstrap/spinnakerContainer.component.ts'), 'utf8'), {
    code: 'ENOENT',
  });
  assert.throws(() => readFileSync(path.join(coreSourceRoot, 'core.module.ts'), 'utf8'), { code: 'ENOENT' });
  assert.throws(() => readFileSync(path.join(coreSourceRoot, 'utils/failedToInstantiateModule.ts'), 'utf8'), {
    code: 'ENOENT',
  });
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

test('production Angular graph scan detects value imports and module aliases', () => {
  withFixtureRoot((fixtureRoot) => {
    const fixtures = new Map([
      ['default.ts', "import angular from 'angular';\nangular.module('default', []);"],
      ['named.ts', "import {\n  module as ngModule,\n} from 'angular';\nngModule('named', []);"],
      ['namespace.ts', "import * as ng from 'angular';\nng.module('namespace', []);"],
      ['require.cjs', "const angular = require('angular');\nangular.module('required', []);"],
      ['side-effect.js', "import 'angular';"],
    ]);
    fixtures.forEach((source, file) => writeFileSync(path.join(fixtureRoot, file), source));

    assert.deepEqual(findProductionAngularGraph(productionSourceFiles(fixtureRoot), fixtureRoot), [
      'default.ts: Angular runtime import',
      'default.ts: Angular module factory',
      'named.ts: Angular runtime import',
      'named.ts: Angular module factory',
      'namespace.ts: Angular runtime import',
      'namespace.ts: Angular module factory',
      'require.cjs: Angular runtime import',
      'require.cjs: Angular module factory',
      'side-effect.js: Angular runtime import',
    ]);
  });
});

test('production Angular graph scan detects ambient module factories without imports', () => {
  withFixtureRoot((fixtureRoot) => {
    const fixtures = new Map([
      ['ambient.ts', "angular.module('regression', []);"],
      ['window.ts', "window.angular.module('regression', []);"],
    ]);
    fixtures.forEach((source, file) => writeFileSync(path.join(fixtureRoot, file), source));

    assert.deepEqual(findProductionAngularGraph(productionSourceFiles(fixtureRoot), fixtureRoot), [
      'ambient.ts: Angular module factory',
      'window.ts: Angular module factory',
    ]);
  });
});

test('production Angular graph scan allows type-only Angular imports', () => {
  withFixtureRoot((fixtureRoot) => {
    writeFileSync(
      path.join(fixtureRoot, 'types.ts'),
      "import type { IQService } from 'angular';\nexport type Q = IQService;",
    );

    assert.deepEqual(findProductionAngularGraph(productionSourceFiles(fixtureRoot), fixtureRoot), []);
  });
});

test('production Angular graph scan detects every Angular registration method', () => {
  withFixtureRoot((fixtureRoot) => {
    angularRegistrationMethods.forEach((method) => {
      writeFileSync(
        path.join(fixtureRoot, `${method}.ts`),
        `import { module } from 'angular';\nconst ngModule = module('fixture', []);\nngModule.${method}('fixture', {});`,
      );
    });

    assert.deepEqual(
      findProductionAngularGraph(productionSourceFiles(fixtureRoot), fixtureRoot),
      angularRegistrationMethods.flatMap((method) => [
        `${method}.ts: Angular runtime import`,
        `${method}.ts: Angular module factory`,
        `${method}.ts: Angular .${method} registration`,
      ]),
    );
  });
});

test('production Angular graph scan detects Angular React adapters in every source extension', () => {
  withFixtureRoot((fixtureRoot) => {
    const adapters = ['angularComponentFromReact', 'react2angular', 'angular2react'];
    const fixtureFiles = adapters
      .flatMap((adapter) => sourceExtensions.map((extension) => `${adapter}${extension}`))
      .sort();
    fixtureFiles.forEach((file) => {
      const adapter = path.basename(file, path.extname(file));
      writeFileSync(path.join(fixtureRoot, file), `${adapter}(Component);`);
    });

    assert.deepEqual(
      findProductionAngularGraph(productionSourceFiles(fixtureRoot), fixtureRoot),
      fixtureFiles.map((file) => `${file}: ${path.basename(file, path.extname(file))}`),
    );
  });
});

test('production Angular graph scan excludes spec and test source', () => {
  withFixtureRoot((fixtureRoot) => {
    const source = "import angular from 'angular';\nangular.module('fixture', []);";
    writeFileSync(path.join(fixtureRoot, 'fixture.spec.ts'), source);
    writeFileSync(path.join(fixtureRoot, 'fixture.test.ts'), source);

    assert.deepEqual(findProductionAngularGraph(productionSourceFiles(fixtureRoot), fixtureRoot), []);
  });
});

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

const assert = require('node:assert/strict');
const { existsSync, readFileSync } = require('node:fs');
const { createRequire } = require('node:module');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const appRoot = path.resolve(__dirname, '..');
const deckRoot = path.resolve(appRoot, '../..');
const repositoryRoot = path.resolve(deckRoot, '..');
const coreRoot = path.resolve(appRoot, '../core');
const coreSourceRoot = path.resolve(coreRoot, 'src');
const requireFromCore = createRequire(path.join(coreRoot, 'package.json'));
const ts = requireFromCore('typescript');

const readAppFile = (relativePath) => readFileSync(path.join(appRoot, relativePath), 'utf8');
const readCoreFile = (relativePath) => readFileSync(path.join(coreSourceRoot, relativePath), 'utf8');
const readDeckFile = (relativePath) => readFileSync(path.join(deckRoot, relativePath), 'utf8');
const readRepositoryFile = (relativePath) => readFileSync(path.join(repositoryRoot, relativePath), 'utf8');
const appPackage = JSON.parse(readAppFile('package.json'));
const deckPackage = JSON.parse(readDeckFile('package.json'));
const forbiddenApplicationWebpackPackages = [
  '@types/webpack-env',
  '@webpack-cli/serve',
  'copy-webpack-plugin',
  'eslint-webpack-plugin',
  'html-webpack-plugin',
  'imports-loader',
  'terser-webpack-plugin',
  'webpack-cli',
  'webpack-dev-server',
];
const karmaAppWebpackDependencies = new Map([
  ['babel-loader', /loader: 'babel-loader'/],
  ['cache-loader', /loader: 'cache-loader'/],
  ['css-loader', /loader: 'css-loader'/],
  ['envify-loader', /loader: 'envify-loader'/],
  ['expose-loader', /loader: 'expose-loader/],
  ['file-loader', /loader: 'file-loader'/],
  ['fork-ts-checker-webpack-plugin', /appRequire\('fork-ts-checker-webpack-plugin'\)/],
  ['less-loader', /loader: 'less-loader'/],
  ['md5', /appRequire\('md5'\)/],
  ['physical-cpu-count', /appRequire\('physical-cpu-count'\)/],
  ['postcss-loader', /loader: 'postcss-loader'/],
  ['source-map-loader', /loader: 'source-map-loader'/],
  ['style-loader', /loader: 'style-loader'/],
  ['thread-loader', /loader: 'thread-loader'/],
  ['ts-loader', /loader: 'ts-loader'/],
  ['webpack', /appRequire\('webpack'\)/],
]);
const rootWebpackDependencyOwners = new Map([
  ['babel-loader', ['.storybook/main.js', /loader: 'babel-loader'/]],
  ['karma-sourcemap-loader', ['karma.conf.js', /require\('karma-sourcemap-loader'\)/]],
  ['karma-webpack', ['karma.conf.js', /require\('karma-webpack'\)/]],
]);
const activeBuildContractFiles = [
  '.dockerignore',
  'build.gradle',
  'Dockerfile.slim',
  'Dockerfile.ubuntu',
  'package.json',
  'packages/app/package.json',
  'packages/app/vite.config.helpers.ts',
  'packages/app/vite.config.ts',
  'packages/app/scripts/vite-build-contract.js',
  'test/functional/vite.config.ts',
  'README.md',
  'test/functional/README.md',
];

function manifestDependencies(manifest) {
  return { ...manifest.dependencies, ...manifest.devDependencies, ...manifest.optionalDependencies };
}

function findForbiddenApplicationWebpackCommands(scripts) {
  const commandPatterns = new Map([
    ['webpack', /(?:^|[\s;&|])webpack(?=$|[\s;&|])/],
    ['webpack-cli', /(?:^|[\s;&|])webpack-cli(?=$|[\s;&|])/],
    ['webpack-dev-server', /(?:^|[\s;&|])webpack-dev-server(?=$|[\s;&|])/],
    ['application webpack config', /(?:packages\/app\/)?webpack\.config(?:\.[cm]?[jt]s)?/],
  ]);

  return Object.entries(scripts).flatMap(([scriptName, command]) =>
    [...commandPatterns].flatMap(([description, pattern]) =>
      pattern.test(command) ? [`${scriptName}: ${description}`] : [],
    ),
  );
}

function loadFunctionalViteConfig(loadEnvironment, processEnvironment = {}) {
  const functionalRoot = path.join(deckRoot, 'test/functional');
  const source = readDeckFile('test/functional/vite.config.ts');
  const compiled = ts.transpileModule(source, {
    compilerOptions: {
      esModuleInterop: true,
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2020,
    },
  }).outputText;
  const configModule = { exports: {} };
  const configRequire = (specifier) => {
    if (specifier === 'autoprefixer') {
      return () => ({});
    }
    if (specifier === 'vite') {
      return { defineConfig: (config) => config, loadEnv: loadEnvironment };
    }
    return require(specifier);
  };

  vm.runInNewContext(compiled, {
    __dirname: functionalRoot,
    exports: configModule.exports,
    module: configModule,
    process: { env: processEnvironment },
    require: configRequire,
  });
  return configModule.exports.default;
}

test('root and app commands expose application typechecking without an experimental Vite path', () => {
  assert.equal(deckPackage.scripts.typecheck, 'pnpm --filter deck-app typecheck');
  assert.equal(deckPackage.scripts['dev:experimental'], undefined);
  assert.equal(appPackage.scripts['dev:experimental'], undefined);
});

test('root and app application commands do not invoke Webpack tooling', () => {
  assert.deepEqual(findForbiddenApplicationWebpackCommands(deckPackage.scripts), []);
  assert.deepEqual(findForbiddenApplicationWebpackCommands(appPackage.scripts), []);
});

test('application command ownership guard rejects mixed Vite and Webpack builds', () => {
  assert.deepEqual(findForbiddenApplicationWebpackCommands({ build: 'vite build && webpack' }), ['build: webpack']);
});

test('application build is Vite-only followed by verification', () => {
  assert.deepEqual(appPackage.scripts.build.split(/\s*&&\s*/), [
    "NODE_OPTIONS='--max_old_space_size=8192' vite build",
    'pnpm verify-build',
  ]);
  assert.equal(appPackage.scripts['start-dev-server'], 'vite');
  assert.equal(appPackage.scripts.typecheck, 'tsc --noEmit');
  assert.match(appPackage.scripts['verify-build'], /vite-build-contract/);
});

test('application packaging uses the Vite task and app-local dist output', () => {
  const gradleBuild = readDeckFile('build.gradle');

  assert.match(gradleBuild, /task\s+viteBuild\s*\(type:\s*PnpmTask\)/);
  assert.doesNotMatch(gradleBuild, /(?:task\s+webpack\b|tasks?(?:\.register|\.create)\s*\(\s*['"]webpack['"])/);
  assert.match(gradleBuild, /viteBuild\.inputs\.file\s+file\(['"]version\.json['"]\)/);
  assert.match(gradleBuild, /viteBuild\.outputs\.dir\s+file\(['"]packages\/app\/dist['"]\)/);
  assert.doesNotMatch(gradleBuild, /\bcopyFavicon\b/);
  assert.match(gradleBuild, /buildDeb\.dependsOn\s+['"]viteBuild['"]/);
  assert.match(gradleBuild, /buildRpm\.dependsOn\s+['"]viteBuild['"]/);
  assert.match(gradleBuild, /ospackage\s*\{[\s\S]*?from\s+['"]packages\/app\/dist['"]/);
  assert.doesNotMatch(gradleBuild, /Paths\.get\(['"]build['"],\s*['"]webpack['"],\s*['"]version\.json['"]\)/);
});

test('release workflow builds the container application with the Vite task', () => {
  const workflow = readRepositoryFile('.github/workflows/deck.yml');

  assert.match(workflow, /docker-build-command:\s*['"]:deck:viteBuild['"]/);
  assert.doesNotMatch(workflow, /:deck:webpack\b/);
});

test('containers deploy app-local dist without changing the runtime path', () => {
  const dockerIgnore = readDeckFile('.dockerignore');

  for (const dockerfile of ['Dockerfile.slim', 'Dockerfile.ubuntu']) {
    assert.match(
      readDeckFile(dockerfile),
      /COPY\s+packages\/app\/dist\s+\/opt\/deck\/html/,
      `${dockerfile} must copy the Vite output to the deployed runtime path`,
    );
  }
  assert.match(dockerIgnore, /^!packages\/app\/$/m);
  assert.match(dockerIgnore, /^!packages\/app\/dist\/$/m);
  assert.match(dockerIgnore, /^!packages\/app\/dist\/\*\*$/m);
  assert.match(dockerIgnore, /^build$/m);
  assert.deepEqual(dockerIgnore.match(/^!packages\/.*$/gm), [
    '!packages/app/',
    '!packages/app/dist/',
    '!packages/app/dist/**',
  ]);
});

test('Vite build configuration, verification, and functional preview share output resolution', () => {
  const viteHelpers = readAppFile('vite.config.helpers.ts');
  const viteConfig = readAppFile('vite.config.ts');
  const buildVerifier = readAppFile('scripts/vite-build-contract.js');
  const viteEnvironment = readAppFile('scripts/vite-environment.js');
  const functionalViteConfig = readDeckFile('test/functional/vite.config.ts');

  assert.equal(existsSync(path.join(appRoot, 'scripts/vite-output-path.js')), true);
  assert.match(viteHelpers, /resolveBuildOutput\(appRoot:\s*string,\s*env:\s*Environment\)/);
  assert.match(viteHelpers, /resolveSharedBuildOutput\(appRoot,\s*env\)/);
  assert.doesNotMatch(viteHelpers, /const outputRoot\s*=\s*path\.resolve\(appRoot,\s*['"]dist['"]\)/);
  assert.match(viteConfig, /resolveBuildOutput\(APP_ROOT,\s*env\)/);
  assert.match(viteConfig, /mergeViteEnvironment\(loadEnv,\s*mode,\s*APP_ROOT,\s*process\.env\)/);
  assert.match(viteEnvironment, /return\s*\{\s*\.\.\.loadEnv\(mode,\s*appRoot,\s*['"]['"]\),\s*\.\.\.processEnv\s*\}/);
  assert.match(buildVerifier, /require\(['"]\.\/vite-output-path(?:\.js)?['"]\)/);
  assert.match(buildVerifier, /const appRoot = path\.resolve\(__dirname,\s*['"]\.\.['"]\)/);
  assert.match(buildVerifier, /mergeViteEnvironment\(loadEnv,\s*['"]production['"],\s*appRoot,\s*process\.env\)/);
  assert.match(buildVerifier, /buildCliArguments\(process\.argv\.slice\(2\),\s*appRoot,\s*deckRoot,\s*env\)/);
  assert.match(functionalViteConfig, /import\s+\{\s*defineConfig,\s*loadEnv\s*\}\s+from\s+['"]vite['"]/);
  assert.match(functionalViteConfig, /appRequire\(['"].*packages\/app\/scripts\/vite-environment(?:\.js)?['"]\)/);
  assert.match(functionalViteConfig, /appRequire\(['"].*packages\/app\/scripts\/vite-output-path(?:\.js)?['"]\)/);
  assert.match(
    functionalViteConfig,
    /mergeViteEnvironment\(loadEnv,\s*['"]production['"],\s*APP_ROOT,\s*process\.env\)/,
  );
  assert.match(functionalViteConfig, /resolveBuildOutput\(APP_ROOT,\s*env\)/);
  assert.doesNotMatch(functionalViteConfig, /resolveBuildOutput\(APP_ROOT,\s*process\.env\)/);
  assert.match(functionalViteConfig, /emptyOutDir:\s*false/);
});

test('functional preview follows app production file output', () => {
  const calls = [];
  const environment = 'file-defined/nested';
  const config = loadFunctionalViteConfig((...args) => {
    calls.push(args);
    return { SPINNAKER_ENV: environment };
  });

  assert.deepEqual(calls, [['production', appRoot, '']]);
  assert.equal(config.build.outDir, path.join(appRoot, 'dist', environment));
});

test('functional documentation uses the repository-relative app output path', () => {
  const functionalReadme = readDeckFile('test/functional/README.md');

  assert.match(functionalReadme, /`packages\/app\/dist`/);
  assert.doesNotMatch(functionalReadme, /`\/packages\/app\/dist`/);
});

test('active build and packaging files contain no legacy build/webpack path', () => {
  const staleReferences = activeBuildContractFiles.filter((filePath) => /build\/webpack/.test(readDeckFile(filePath)));

  assert.deepEqual(staleReferences, []);
});

test('app clean removes only app-local dist with Node', () => {
  assert.match(appPackage.scripts.clean, /^node -e /);
  assert.match(appPackage.scripts.clean, /rmSync\(['"]dist['"],\s*\{\s*recursive:\s*true,\s*force:\s*true\s*\}\)/);
  assert.doesNotMatch(appPackage.scripts.clean, /\.\.\/\.\.(?:\/build|\/\.cache-loader)/);
});

test('root and app manifests exclude application-only Webpack dependencies', () => {
  const rootDependencies = manifestDependencies(deckPackage);
  const appDependencies = manifestDependencies(appPackage);

  forbiddenApplicationWebpackPackages.forEach((packageName) => {
    assert.equal(rootDependencies[packageName], undefined, `${packageName} remains in the root manifest`);
    assert.equal(appDependencies[packageName], undefined, `${packageName} remains in the app manifest`);
  });
});

test('temporary app Webpack dependencies are an exact Karma-owned allowlist', () => {
  const karmaWebpackConfig = readDeckFile('test/karma/webpack.config.js');
  const webpackDependencies = Object.keys(appPackage.devDependencies)
    .filter(
      (packageName) =>
        packageName === 'webpack' ||
        packageName.endsWith('-loader') ||
        packageName.endsWith('-webpack-plugin') ||
        packageName === 'md5' ||
        packageName === 'physical-cpu-count',
    )
    .sort();

  assert.deepEqual(webpackDependencies, [...karmaAppWebpackDependencies.keys()].sort());
  karmaAppWebpackDependencies.forEach((usagePattern, packageName) => {
    assert.match(karmaWebpackConfig, usagePattern, `${packageName} must be referenced by the Karma Webpack config`);
  });
});

test('root Webpack-related dependencies have explicit config owners', () => {
  const karmaConfig = readDeckFile('karma.conf.js');
  const webpackDependencies = Object.keys(deckPackage.devDependencies)
    .filter(
      (packageName) =>
        packageName === 'webpack' ||
        packageName === 'karma-webpack' ||
        packageName.endsWith('-loader') ||
        packageName.endsWith('-webpack-plugin'),
    )
    .sort();

  assert.deepEqual(webpackDependencies, [...rootWebpackDependencyOwners.keys()].sort());
  rootWebpackDependencyOwners.forEach(([configPath, usagePattern], packageName) => {
    assert.match(readDeckFile(configPath), usagePattern, `${packageName} must be referenced by ${configPath}`);
  });
  assert.match(karmaConfig, /'\.\/karma-shim\.js': \['webpack', 'sourcemap'\]/);
});

test('Core leaves lazy Kayenta loading to the app build without requiring Kayenta dist', () => {
  const sharedLibrariesSource = readCoreFile('plugins/sharedLibraries.ts');
  const sourceFile = ts.createSourceFile('sharedLibraries.ts', sharedLibrariesSource, ts.ScriptTarget.Latest, true);
  let kayentaImportArgument;

  const findKayentaImport = (node) => {
    if (ts.isCallExpression(node) && node.expression.kind === ts.SyntaxKind.ImportKeyword) {
      const [argument] = node.arguments;
      if (argument?.getText(sourceFile).includes('@spinnaker/kayenta')) {
        kayentaImportArgument = argument.getText(sourceFile);
      }
    }
    ts.forEachChild(node, findKayentaImport);
  };
  findKayentaImport(sourceFile);

  assert.ok(kayentaImportArgument, 'expected a lazy Kayenta import');

  const virtualFile = path.join(coreRoot, '__unbuilt_kayenta_contract__.ts');
  const virtualSource = `void import(${kayentaImportArgument});`;
  const compilerOptions = {
    baseUrl: coreRoot,
    module: ts.ModuleKind.ESNext,
    moduleResolution: ts.ModuleResolutionKind.Node10,
    noEmit: true,
    noLib: true,
    paths: { '@spinnaker/kayenta': ['__missing_kayenta_dist__'] },
    target: ts.ScriptTarget.ES2019,
  };
  const compilerHost = ts.createCompilerHost(compilerOptions);
  compilerHost.fileExists = (fileName) => fileName === virtualFile;
  compilerHost.readFile = (fileName) => (fileName === virtualFile ? virtualSource : undefined);
  compilerHost.getSourceFile = (fileName) =>
    fileName === virtualFile
      ? ts.createSourceFile(fileName, virtualSource, ts.ScriptTarget.ES2019, true, ts.ScriptKind.TS)
      : undefined;

  const diagnostics = ts
    .createProgram([virtualFile], compilerOptions, compilerHost)
    .getSemanticDiagnostics()
    .filter(({ code }) => code === 2307);
  assert.deepEqual(
    diagnostics.map(({ messageText }) => messageText),
    [],
  );

  const emittedSource = ts.transpileModule(virtualSource, { compilerOptions }).outputText;
  assert.match(emittedSource, /import\(['"]@spinnaker\/kayenta['"]\)/);
  assert.doesNotMatch(emittedSource, /\brequire\(/);

  const configureExternals = require(path.resolve(coreRoot, '../scripts/helpers/rollup-node-auto-external-configurer'));
  const corePackage = JSON.parse(readFileSync(path.join(coreRoot, 'package.json'), 'utf8'));
  assert.equal(configureExternals(corePackage.dependencies)('@spinnaker/kayenta'), true);
});

test('index.html exposes the React root', () => {
  const html = readAppFile('index.html');

  assert.match(html, /<html class="no-js">/);
  assert.match(html, /<div id="spinnaker-root"><\/div>/);
});

test('Vite owns the app build while Webpack remains isolated to Karma', () => {
  assert.equal(existsSync(path.join(appRoot, 'webpack.config.js')), false);
  assert.equal(existsSync(path.join(appRoot, 'index.deck')), false);
  assert.equal(existsSync(path.join(appRoot, 'vite.config.ts')), true);
  assert.equal(existsSync(path.join(deckRoot, 'test/karma/webpack.config.js')), true);
});

test('app entry bootstraps Deck at the configured root', () => {
  const appEntry = readAppFile('src/app.ts');

  assert.match(appEntry, /import \{[^}]*\bbootstrapDeck\b[^}]*\} from '@spinnaker\/core';/);
  assert.match(
    appEntry,
    /void bootstrapDeck\(document\.getElementById\('spinnaker-root'\)\)\.catch\(\(error\) => \{\s*console\.error\('Deck bootstrap failed', error\);\s*\}\);/,
  );
  assert.doesNotMatch(appEntry, /registerPreconfiguredJobStages/);
  assert.doesNotMatch(appEntry, /registerPreconfiguredWebhookStages/);
});

test('app entry leaves settings ownership to the configured settings bundle', () => {
  const appEntry = readAppFile('src/app.ts');
  const viteConfig = readAppFile('vite.config.ts');

  assert.doesNotMatch(appEntry, /import ['"]\.\/settings(?:\.js)?['"];?/);
  assert.match(viteConfig, /const settingsPath = resolveSettingsPath\(APP_ROOT, env\);/);
  assert.match(viteConfig, /settings: settingsPath/);
});

test('Karma owns its temporary Webpack preprocessing without importing the app build', () => {
  const karmaConfig = readDeckFile('karma.conf.js');
  const karmaWebpackConfig = readDeckFile('test/karma/webpack.config.js');

  assert.doesNotMatch(karmaConfig, /packages\/app\/webpack\.config/);
  assert.match(karmaConfig, /test\/karma\/webpack\.config/);
  assert.doesNotMatch(karmaWebpackConfig, /packages\/app\/webpack\.config/);
  assert.match(karmaWebpackConfig, /webpackImportMetaLoader/);
});

test('direct bootstrap owns global styles and browser initialization', () => {
  const bootstrapSource = readCoreFile('bootstrap/bootstrapDeck.tsx');

  assert.match(bootstrapSource, /import 'bootstrap\/dist\/css\/bootstrap\.css';/);
  assert.match(bootstrapSource, /import '\.\.\/fonts\/icons\.css';/);
  assert.match(bootstrapSource, /import \{ domPurifyOpenLinksInNewWindow \}/);
  assert.match(bootstrapSource, /import \{ initGoogleAnalytics \}/);
  assert.match(bootstrapSource, /domPurifyOpenLinksInNewWindow\(\);/);
  assert.match(bootstrapSource, /initGoogleAnalytics\(\);/);
});

test('direct bootstrap loads infrastructure styles after presentation defaults', () => {
  const bootstrapSource = readCoreFile('bootstrap/bootstrapDeck.tsx');
  const runtimeInitializersSource = readCoreFile('bootstrap/runtimeInitializers.ts');
  const presentationStyles = bootstrapSource.indexOf("import '../presentation/main.less';");
  const infrastructureStyles = bootstrapSource.indexOf("import '../search/infrastructure/infrastructure.less';");

  assert.notEqual(presentationStyles, -1);
  assert.ok(infrastructureStyles > presentationStyles);
  assert.doesNotMatch(runtimeInitializersSource, /search\/infrastructure\/infrastructure\.less/);
});

test('direct bootstrap loads Google Analytics from the analytics directory', () => {
  const bootstrapSource = readCoreFile('bootstrap/bootstrapDeck.tsx');

  assert.match(bootstrapSource, /from '\.\.\/analytics\/react\.ga';/);
  assert.doesNotMatch(bootstrapSource, /from '\.\.\/reactShims\/react\.ga';/);
  assert.equal(existsSync(path.join(coreSourceRoot, 'analytics/react.ga.ts')), true);
  assert.equal(existsSync(path.join(coreSourceRoot, 'reactShims/react.ga.ts')), false);
});

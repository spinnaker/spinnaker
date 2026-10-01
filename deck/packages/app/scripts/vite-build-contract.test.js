const assert = require('node:assert/strict');
const { spawnSync } = require('node:child_process');
const {
  copyFileSync,
  existsSync,
  mkdirSync,
  mkdtempSync,
  readFileSync,
  realpathSync,
  rmSync,
  symlinkSync,
  unlinkSync,
  writeFileSync,
} = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const test = require('node:test');

const {
  buildCliArguments,
  parseCliArguments,
  verifyDeckBuild,
  verifySourceArtifacts,
} = require('./vite-build-contract');

const CONTRACT_SCRIPT = path.resolve(__dirname, 'vite-build-contract.js');
const VERSION = { version: '1.2.3', commit: 'abc123' };
const USAGE = /Usage: vite-build-contract\.js/;

function writeMappedJavaScript(filePath, code, mapOverrides = {}) {
  const mapFileName = `${path.basename(filePath)}.map`;
  writeFileSync(filePath, `${code}\n//# sourceMappingURL=${mapFileName}\n`);
  writeFileSync(
    `${filePath}.map`,
    JSON.stringify({
      version: 3,
      file: path.basename(filePath),
      sources: [`${path.basename(filePath)}.source.js`],
      sourcesContent: [code],
      names: [],
      mappings: 'AAAA',
      ...mapOverrides,
    }),
  );
}

function createFixture(t) {
  const root = mkdtempSync(path.join(os.tmpdir(), 'deck-vite-build-contract-'));
  const outputDir = path.join(root, 'build');
  const assetsDir = path.join(outputDir, 'assets');
  const expectedVersionPath = path.join(root, 'version.json');
  const sourceRoot = path.join(root, 'sources');
  const pluginManifestPath = path.join(sourceRoot, 'plugin-manifest.json');
  const styleguidePath = path.join(sourceRoot, 'styleguide.html');
  const faviconPath = path.join(sourceRoot, 'prod-favicon.ico');

  mkdirSync(assetsDir, { recursive: true });
  mkdirSync(sourceRoot, { recursive: true });
  writeFileSync(
    path.join(outputDir, 'index.html'),
    [
      '<script src="./settings.js"></script>',
      '<script src="./settings-local.js"></script>',
      '<script type="module" src="./assets/app-abc123.js"></script>',
    ].join('\n'),
  );
  writeMappedJavaScript(path.join(outputDir, 'settings.js'), 'window.spinnakerSettings = {};');
  writeMappedJavaScript(
    path.join(outputDir, 'settings-local.js'),
    'window.spinnakerSettings = window.spinnakerSettings || {};',
  );
  writeFileSync(path.join(outputDir, 'plugin-manifest.json'), '{}');
  writeFileSync(path.join(outputDir, 'styleguide.html'), '<!doctype html>');
  writeFileSync(path.join(outputDir, 'favicon.ico'), 'favicon');
  writeFileSync(path.join(outputDir, 'version.json'), JSON.stringify(VERSION));
  writeMappedJavaScript(path.join(assetsDir, 'app-abc123.js'), 'console.log("deck");');
  writeFileSync(expectedVersionPath, JSON.stringify(VERSION));
  writeFileSync(pluginManifestPath, '{}');
  writeFileSync(styleguidePath, '<!doctype html>');
  writeFileSync(faviconPath, 'favicon');

  t.after(() => rmSync(root, { recursive: true, force: true }));
  return {
    outputDir,
    expectedVersionPath,
    sources: {
      pluginManifestPath,
      versionPath: expectedVersionPath,
      styleguidePath,
      faviconPath,
    },
  };
}

function writeCliOutput(outputDir, settingsSource, sources) {
  const assetsDir = path.join(outputDir, 'assets');
  mkdirSync(assetsDir, { recursive: true });
  writeFileSync(
    path.join(outputDir, 'index.html'),
    [
      '<script src="./settings.js"></script>',
      '<script src="./settings-local.js"></script>',
      '<script type="module" src="./assets/app.js"></script>',
    ].join('\n'),
  );
  writeMappedJavaScript(path.join(outputDir, 'settings.js'), settingsSource);
  writeMappedJavaScript(path.join(outputDir, 'settings-local.js'), 'window.spinnakerSettings ||= {};');
  writeMappedJavaScript(path.join(assetsDir, 'app.js'), 'console.log("deck");');
  writeFileSync(path.join(outputDir, 'plugin-manifest.json'), readFileSync(sources.pluginManifestPath));
  writeFileSync(path.join(outputDir, 'styleguide.html'), readFileSync(sources.styleguidePath));
  writeFileSync(path.join(outputDir, 'favicon.ico'), readFileSync(sources.faviconPath));
  writeFileSync(path.join(outputDir, 'version.json'), readFileSync(sources.versionPath));
}

function createCliFixture(t) {
  const root = mkdtempSync(path.join(os.tmpdir(), 'deck-vite-build-cli-'));
  const deckRoot = path.join(root, 'deck');
  const appRoot = path.join(deckRoot, 'packages', 'app');
  const scriptsRoot = path.join(appRoot, 'scripts');
  const pluginManifestPath = path.join(appRoot, 'public', 'plugin-manifest.json');
  const faviconPath = path.join(appRoot, 'icons', 'prod-favicon.ico');
  const versionPath = path.join(deckRoot, 'version.json');
  const defaultSettingsPath = path.join(appRoot, 'src', 'settings.js');
  const fileSettingsPath = path.join(deckRoot, 'halconfig', 'settings.js');
  const processSettingsPath = path.join(root, 'process-settings.js');
  const styleguidePackage = require.resolve('@spinnaker/styleguide/package.json', {
    paths: [path.resolve(__dirname, '..')],
  });
  const styleguidePath = path.join(path.dirname(styleguidePackage), 'public', 'styleguide.html');
  const sourceScriptsRoot = __dirname;

  mkdirSync(scriptsRoot, { recursive: true });
  mkdirSync(path.dirname(pluginManifestPath), { recursive: true });
  mkdirSync(path.dirname(faviconPath), { recursive: true });
  mkdirSync(path.dirname(defaultSettingsPath), { recursive: true });
  mkdirSync(path.dirname(fileSettingsPath), { recursive: true });
  symlinkSync(path.resolve(__dirname, '../../../node_modules'), path.join(deckRoot, 'node_modules'), 'dir');
  symlinkSync(path.resolve(__dirname, '../node_modules'), path.join(appRoot, 'node_modules'), 'dir');
  for (const scriptName of ['vite-build-contract.js', 'vite-output-path.js', 'vite-environment.js']) {
    const sourcePath = path.join(sourceScriptsRoot, scriptName);
    if (existsSync(sourcePath)) {
      copyFileSync(sourcePath, path.join(scriptsRoot, scriptName));
    }
  }

  writeFileSync(
    path.join(appRoot, '.env.production'),
    'SPINNAKER_ENV=from-file\nSETTINGS_PATH=../../halconfig/settings.js\n',
  );
  writeFileSync(defaultSettingsPath, "window.settingsSource = '{%stale.root%}';");
  writeFileSync(fileSettingsPath, "window.settingsSource = '{%from.file%}';");
  writeFileSync(processSettingsPath, "window.settingsSource = '{%from.process%}';");
  writeFileSync(pluginManifestPath, '{}');
  writeFileSync(faviconPath, 'favicon');
  writeFileSync(versionPath, JSON.stringify(VERSION));

  const sources = { pluginManifestPath, faviconPath, versionPath, styleguidePath };
  writeCliOutput(path.join(appRoot, 'dist'), readFileSync(defaultSettingsPath, 'utf8'), sources);
  writeCliOutput(path.join(appRoot, 'dist', 'from-file'), readFileSync(fileSettingsPath, 'utf8'), sources);
  writeCliOutput(path.join(appRoot, 'dist', 'from-process'), readFileSync(processSettingsPath, 'utf8'), sources);

  const cleanEnv = { ...process.env };
  delete cleanEnv.SPINNAKER_ENV;
  delete cleanEnv.SETTINGS_PATH;
  t.after(() => rmSync(root, { recursive: true, force: true }));
  return {
    appRoot,
    cleanEnv,
    contractScript: path.join(scriptsRoot, 'vite-build-contract.js'),
    fileOutputDir: path.join(appRoot, 'dist', 'from-file'),
    processOutputDir: path.join(appRoot, 'dist', 'from-process'),
    processSettingsPath,
  };
}

test('accepts a complete runtime artifact contract', (t) => {
  const { outputDir, expectedVersionPath } = createFixture(t);

  assert.doesNotThrow(() => verifyDeckBuild(outputDir, expectedVersionPath));
});

test('accepts query and hash suffixes on local script URLs', (t) => {
  const { outputDir } = createFixture(t);
  writeFileSync(
    path.join(outputDir, 'index.html'),
    [
      '<script src="./settings.js?version=1"></script>',
      '<script src="./settings-local.js#local"></script>',
      '<script type="module" src="./assets/app-abc123.js?version=1#app"></script>',
    ].join('\n'),
  );

  assert.doesNotThrow(() => verifyDeckBuild(outputDir));
});

test('rejects a missing settings-local.js', (t) => {
  const { outputDir } = createFixture(t);
  unlinkSync(path.join(outputDir, 'settings-local.js'));

  assert.throws(() => verifyDeckBuild(outputDir), /settings-local\.js/);
});

test('rejects the app module before settings.js and settings-local.js', (t) => {
  const { outputDir } = createFixture(t);
  writeFileSync(
    path.join(outputDir, 'index.html'),
    [
      '<script type="module" src="./assets/app-abc123.js"></script>',
      '<script src="./settings.js"></script>',
      '<script src="./settings-local.js"></script>',
    ].join('\n'),
  );

  assert.throws(() => verifyDeckBuild(outputDir), /settings\.js.*settings-local\.js.*app module/);
});

test('rejects missing JavaScript source maps', (t) => {
  const { outputDir } = createFixture(t);
  unlinkSync(path.join(outputDir, 'assets', 'app-abc123.js.map'));

  assert.throws(() => verifyDeckBuild(outputDir), /source map.*app-abc123\.js/i);
});

test('rejects a missing local app module referenced by index.html', (t) => {
  const { outputDir } = createFixture(t);
  unlinkSync(path.join(outputDir, 'assets', 'app-abc123.js'));

  assert.throws(() => verifyDeckBuild(outputDir), /Missing local script.*assets\/app-abc123\.js/);
});

test('rejects a module stylesheet when no application JavaScript is loaded', (t) => {
  const { outputDir } = createFixture(t);
  unlinkSync(path.join(outputDir, 'assets', 'app-abc123.js'));
  unlinkSync(path.join(outputDir, 'assets', 'app-abc123.js.map'));
  writeFileSync(path.join(outputDir, 'assets', 'app.css'), 'body {}');
  writeFileSync(
    path.join(outputDir, 'index.html'),
    [
      '<script src="./settings.js"></script>',
      '<script src="./settings-local.js"></script>',
      '<script type="module" src="./assets/app.css"></script>',
    ].join('\n'),
  );

  assert.throws(() => verifyDeckBuild(outputDir), /app module.*\.\/assets\/.*\.js/i);
});

test('rejects a local script reference that resolves to a directory', (t) => {
  const { outputDir } = createFixture(t);
  writeFileSync(
    path.join(outputDir, 'index.html'),
    [
      '<script src="./assets"></script>',
      '<script src="./settings.js"></script>',
      '<script src="./settings-local.js"></script>',
      '<script type="module" src="./assets/app-abc123.js"></script>',
    ].join('\n'),
  );

  assert.throws(() => verifyDeckBuild(outputDir), /local script.*regular file.*assets/i);
});

test('ignores HTTP and HTTPS scripts referenced by index.html', (t) => {
  const { outputDir } = createFixture(t);
  writeFileSync(
    path.join(outputDir, 'index.html'),
    [
      '<script src="https://cdn.example.com/library.js"></script>',
      '<script src="http://cdn.example.com/legacy.js"></script>',
      '<script src="./settings.js"></script>',
      '<script src="./settings-local.js"></script>',
      '<script type="module" src="./assets/app-abc123.js"></script>',
    ].join('\n'),
  );

  assert.doesNotThrow(() => verifyDeckBuild(outputDir));
});

test('rejects JavaScript without a sourceMappingURL', (t) => {
  const { outputDir } = createFixture(t);
  writeFileSync(path.join(outputDir, 'assets', 'app-abc123.js'), 'console.log("deck");');

  assert.throws(() => verifyDeckBuild(outputDir), /sourceMappingURL.*app-abc123\.js/i);
});

test('rejects a sourceMappingURL that references a missing map', (t) => {
  const { outputDir } = createFixture(t);
  writeFileSync(
    path.join(outputDir, 'assets', 'app-abc123.js'),
    'console.log("deck");\n//# sourceMappingURL=missing.js.map\n',
  );

  assert.throws(() => verifyDeckBuild(outputDir), /Referenced source map.*missing\.js\.map/);
});

test('rejects malformed and incomplete source maps', (t) => {
  const { outputDir } = createFixture(t);
  const mapPath = path.join(outputDir, 'assets', 'app-abc123.js.map');

  writeFileSync(mapPath, '{');
  assert.throws(() => verifyDeckBuild(outputDir), /Invalid source map JSON.*app-abc123\.js\.map/);

  writeFileSync(mapPath, '{}');
  assert.throws(() => verifyDeckBuild(outputDir), /Invalid source map.*app-abc123\.js\.map/);
});

test('rejects source maps with undecodable mappings', (t) => {
  const { outputDir } = createFixture(t);
  writeMappedJavaScript(path.join(outputDir, 'assets', 'app-abc123.js'), 'console.log("deck");', { mappings: '!' });

  assert.throws(() => verifyDeckBuild(outputDir), /Invalid source map mappings.*app-abc123\.js\.map/);
});

test('rejects must-not-leak sentinels in source maps', (t) => {
  const { outputDir } = createFixture(t);
  writeMappedJavaScript(path.join(outputDir, 'assets', 'app-abc123.js'), 'console.log("deck");', {
    sources: ['must-not-leak.js'],
  });

  assert.throws(() => verifyDeckBuild(outputDir), /must-not-leak.*app-abc123\.js\.map/);
});

test('rejects orphan JavaScript source maps', (t) => {
  const { outputDir } = createFixture(t);
  writeFileSync(
    path.join(outputDir, 'assets', 'orphan.js.map'),
    JSON.stringify({
      version: 3,
      file: 'orphan.js',
      sources: ['must-not-leak.js'],
      sourcesContent: ['const secret = "must-not-leak";'],
      names: [],
      mappings: 'AAAA',
    }),
  );

  assert.throws(() => verifyDeckBuild(outputDir), /orphan source map.*orphan\.js\.map/i);
});

test('rejects leaked must-not-leak sentinels in emitted JavaScript', (t) => {
  const { outputDir } = createFixture(t);
  writeMappedJavaScript(path.join(outputDir, 'assets', 'app-abc123.js'), 'const secret = "must-not-leak";');

  assert.throws(() => verifyDeckBuild(outputDir), /must-not-leak/);
});

test('rejects Vite browser externals in emitted JavaScript artifacts', (t) => {
  const { outputDir } = createFixture(t);
  writeFileSync(
    path.join(outputDir, 'assets', 'app-abc123.js.map'),
    JSON.stringify({
      version: 3,
      file: 'app-abc123.js',
      sources: ['\u0000__vite-browser-external:stream'],
      sourcesContent: ['console.log("deck");'],
      names: [],
      mappings: 'AAAA',
    }),
  );

  assert.throws(() => verifyDeckBuild(outputDir), /browser external/);
});

test('rejects output version.json that differs from the expected version', (t) => {
  const { outputDir, expectedVersionPath } = createFixture(t);
  writeFileSync(path.join(outputDir, 'version.json'), JSON.stringify({ version: 'different' }));

  assert.throws(() => verifyDeckBuild(outputDir, expectedVersionPath), /version\.json/);
});

test('accepts a complete template token multiset', (t) => {
  const { outputDir, expectedVersionPath } = createFixture(t);
  const templateSourcePath = path.join(outputDir, '..', 'settings-template.js');
  writeFileSync(templateSourcePath, "const values = ['{%one%}', '{%two%}', '{%one%}'];");
  writeMappedJavaScript(path.join(outputDir, 'settings.js'), "window.values=['{%one%}','{%two%}','{%one%}'];");

  assert.doesNotThrow(() => verifyDeckBuild(outputDir, expectedVersionPath, templateSourcePath));
});

test('rejects a missing template token occurrence', (t) => {
  const { outputDir, expectedVersionPath } = createFixture(t);
  const templateSourcePath = path.join(outputDir, '..', 'settings-template.js');
  writeFileSync(templateSourcePath, "const values = ['{%one%}', '{%two%}', '{%one%}'];");
  writeMappedJavaScript(path.join(outputDir, 'settings.js'), "window.values=['{%one%}','{%two%}'];");

  assert.throws(
    () => verifyDeckBuild(outputDir, expectedVersionPath, templateSourcePath),
    /Missing template token.*\{%one%\}/,
  );
});

test('ordinary verification accepts arbitrary tokenized settings without an expected source', (t) => {
  const { outputDir, expectedVersionPath } = createFixture(t);
  writeMappedJavaScript(path.join(outputDir, 'settings.js'), "window.customSetting = '{%custom.token%}';");

  assert.doesNotThrow(() => verifyDeckBuild(outputDir, expectedVersionPath));
});

test('source-aware verification accepts complete fixed copies', (t) => {
  const { outputDir, sources } = createFixture(t);

  assert.doesNotThrow(() => verifySourceArtifacts(outputDir, sources));
});

test('source-aware verification rejects a missing fixed copy', (t) => {
  const { outputDir, sources } = createFixture(t);
  unlinkSync(path.join(outputDir, 'favicon.ico'));

  assert.throws(() => verifySourceArtifacts(outputDir, sources), /favicon\.ico/);
});

test('source-aware verification identifies changed fixed copied content and source', (t) => {
  const { outputDir, sources } = createFixture(t);
  writeFileSync(path.join(outputDir, 'plugin-manifest.json'), '{"changed":true}');

  assert.throws(
    () => verifySourceArtifacts(outputDir, sources),
    /plugin-manifest\.json.*source.*plugin-manifest\.json/i,
  );
});

test('CLI argument parser preserves supported invocation forms', () => {
  const defaults = { outputDir: '/default/output', expectedVersionPath: '/default/version.json' };

  assert.deepEqual(parseCliArguments([], defaults), { ...defaults, templateSourcePath: undefined });
  assert.deepEqual(parseCliArguments(['--'], defaults), { ...defaults, templateSourcePath: undefined });
  assert.deepEqual(parseCliArguments(['/output'], defaults), {
    outputDir: '/output',
    expectedVersionPath: '/default/version.json',
    templateSourcePath: undefined,
  });
  assert.deepEqual(parseCliArguments(['--', '/output', '/version.json', '/settings.js'], defaults), {
    outputDir: '/output',
    expectedVersionPath: '/version.json',
    templateSourcePath: '/settings.js',
  });
});

test('CLI defaults resolve safe environments and reject path escapes', () => {
  const appRoot = '/deck/packages/app';
  const deckRoot = '/deck';

  assert.equal(typeof buildCliArguments, 'function');
  assert.deepEqual(buildCliArguments([], appRoot, deckRoot, {}), {
    outputDir: '/deck/packages/app/dist',
    expectedVersionPath: '/deck/version.json',
    templateSourcePath: '/deck/packages/app/src/settings.js',
  });
  assert.deepEqual(buildCliArguments([], appRoot, deckRoot, { SPINNAKER_ENV: 'parity' }), {
    outputDir: '/deck/packages/app/dist/parity',
    expectedVersionPath: '/deck/version.json',
    templateSourcePath: '/deck/packages/app/src/settings.js',
  });

  const invalidEnvironments = [
    '/absolute-env',
    '\\\\server\\share',
    'C:\\absolute-env',
    'C:/absolute-env',
    'C:drive-relative',
    '..',
    '../escape',
    '..\\escape',
    'prod/../escape',
    'prod\\..\\escape',
  ];
  invalidEnvironments.forEach((SPINNAKER_ENV) =>
    assert.throws(
      () => buildCliArguments([], appRoot, deckRoot, { SPINNAKER_ENV }),
      /Invalid SPINNAKER_ENV/,
      SPINNAKER_ENV,
    ),
  );
  assert.deepEqual(buildCliArguments(['/explicit/output'], appRoot, deckRoot, { SPINNAKER_ENV: '../escape' }), {
    outputDir: '/explicit/output',
    expectedVersionPath: '/deck/version.json',
    templateSourcePath: '/deck/packages/app/src/settings.js',
  });
});

test('CLI defaults verify the configured settings source while explicit arguments take precedence', () => {
  const appRoot = '/deck/packages/app';
  const deckRoot = '/deck';

  assert.equal(
    buildCliArguments([], appRoot, deckRoot, { SETTINGS_PATH: '../../halconfig/settings.js' }).templateSourcePath,
    '/deck/halconfig/settings.js',
  );
  assert.equal(
    buildCliArguments([], appRoot, deckRoot, { SETTINGS_PATH: '/tmp/custom-settings.js' }).templateSourcePath,
    '/tmp/custom-settings.js',
  );
  assert.equal(
    buildCliArguments(['/output', '/version.json', '/explicit-settings.js'], appRoot, deckRoot, {
      SETTINGS_PATH: '/tmp/custom-settings.js',
    }).templateSourcePath,
    '/explicit-settings.js',
  );
});

test('shared Vite environment merge uses the requested mode and process precedence', () => {
  const { mergeViteEnvironment } = require('./vite-environment');
  const calls = [];
  const loadEnv = (...args) => {
    calls.push(args);
    return { FILE_ONLY: 'file', OVERRIDDEN: 'file' };
  };

  assert.deepEqual(mergeViteEnvironment(loadEnv, 'production', '/deck/packages/app', { OVERRIDDEN: 'process' }), {
    FILE_ONLY: 'file',
    OVERRIDDEN: 'process',
  });
  assert.deepEqual(calls, [['production', '/deck/packages/app', '']]);
});

test('CLI loads production file defaults instead of selecting valid stale root output', (t) => {
  const { appRoot, cleanEnv, contractScript, fileOutputDir } = createCliFixture(t);
  const result = spawnSync(process.execPath, [contractScript], { cwd: appRoot, env: cleanEnv, encoding: 'utf8' });

  assert.equal(result.status, 0, result.stderr);
  assert.equal(result.stdout.trim(), `Verified Deck Vite build: ${realpathSync(fileOutputDir)}`);
});

test('process environment overrides production file output and settings defaults', (t) => {
  const { appRoot, cleanEnv, contractScript, processOutputDir, processSettingsPath } = createCliFixture(t);
  const result = spawnSync(process.execPath, [contractScript], {
    cwd: appRoot,
    env: {
      ...cleanEnv,
      SETTINGS_PATH: processSettingsPath,
      SPINNAKER_ENV: 'from-process',
    },
    encoding: 'utf8',
  });

  assert.equal(result.status, 0, result.stderr);
  assert.equal(result.stdout.trim(), `Verified Deck Vite build: ${realpathSync(processOutputDir)}`);
});

test('environment-selected verifier failures exit non-zero', (t) => {
  const { appRoot, cleanEnv, contractScript, processSettingsPath } = createCliFixture(t);
  const result = spawnSync(process.execPath, [contractScript], {
    cwd: appRoot,
    env: {
      ...cleanEnv,
      SETTINGS_PATH: processSettingsPath,
      SPINNAKER_ENV: 'missing-output',
    },
    encoding: 'utf8',
  });

  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /Missing required build artifact: index\.html/);
});

test('CLI rejects malformed and extra arguments with usage text', () => {
  const defaults = { outputDir: '/default/output', expectedVersionPath: '/default/version.json' };
  const invalidArguments = [
    ['--', '--'],
    ['/output', '--'],
    ['/output', '/version.json', '/settings.js', '/extra'],
    [''],
  ];

  invalidArguments.forEach((args) => assert.throws(() => parseCliArguments(args, defaults), USAGE));

  const result = spawnSync(process.execPath, [CONTRACT_SCRIPT, '--', 'one', 'two', 'three', 'four'], {
    encoding: 'utf8',
  });
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, USAGE);
});

const assert = require('node:assert/strict');
const { mkdtempSync, readFileSync, rmSync, writeFileSync } = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const test = require('node:test');
const { pathToFileURL } = require('node:url');

const appRoot = path.resolve(__dirname, '..');
const viteConfigPath = path.join(appRoot, 'vite.config.ts');
const helpersUrl = pathToFileURL(path.resolve(__dirname, '../vite.config.helpers.ts'));
const viteEnvironmentUrl = pathToFileURL(path.resolve(__dirname, './vite-environment.js'));
const viteModuleUrl = pathToFileURL(require.resolve('vite'));
const loadHelpers = () => import(helpersUrl);
const viteConfigSource = readFileSync(viteConfigPath, 'utf8');
const NEUTRAL_VITE_ENV = {
  DECK_CA_CERT: '',
  DECK_CERT: '',
  DECK_HOST: '',
  DECK_HTTPS: 'false',
  DECK_KEY: '',
  DECK_PORT: '',
  SETTINGS_PATH: '',
  SPINNAKER_ENV: '',
};

async function withViteEnvironment(overrides, callback) {
  const { IMPORT_META_ENV_KEYS, PROCESS_ENV_KEYS } = await loadHelpers();
  const neutralPublicEnvironment = Object.fromEntries(
    [...new Set([...IMPORT_META_ENV_KEYS, ...PROCESS_ENV_KEYS])].map((key) => [key, '']),
  );
  const environment = { ...neutralPublicEnvironment, ...NEUTRAL_VITE_ENV, ...overrides };
  const previous = new Map(
    Object.keys(environment).map((key) => [key, { present: Object.hasOwn(process.env, key), value: process.env[key] }]),
  );

  for (const [key, value] of Object.entries(environment)) {
    if (value === undefined) {
      delete process.env[key];
    } else {
      process.env[key] = value;
    }
  }

  try {
    return await callback();
  } finally {
    for (const [key, { present, value }] of previous) {
      if (present) {
        process.env[key] = value;
      } else {
        delete process.env[key];
      }
    }
  }
}

async function startConfiguredViteServer() {
  const { createServer } = await import('vite');
  const server = await createServer({
    configFile: viteConfigPath,
    logLevel: 'silent',
    server: { host: '127.0.0.1', port: 0, strictPort: false },
  });
  await server.listen();
  return server;
}

function serverOrigin(server, protocol = 'http') {
  const address = server.httpServer?.address();
  assert.ok(address && typeof address !== 'string');
  return `${protocol}://127.0.0.1:${address.port}`;
}

function fsModuleUrl(filePath) {
  return `/@fs/${filePath.split(path.sep).join('/')}`;
}

test('buildPublicEnvDefines exposes only explicitly allowed settings variables', async () => {
  const { buildPublicEnvDefines, IMPORT_META_ENV_KEYS, PROCESS_ENV_KEYS } = await loadHelpers();
  const definitions = buildPublicEnvDefines({
    API_HOST: 'https://gate.example.com',
    VITE_API_HOST: 'https://vite-gate.example.com',
    VITE_UNRELATED_SECRET: 'vite-secret',
    UNRELATED_SECRET: 'process-secret',
  });
  const expectedExpressions = [
    ...IMPORT_META_ENV_KEYS.map((key) => `import.meta.env.${key}`),
    ...PROCESS_ENV_KEYS.map((key) => `process.env.${key}`),
  ].sort();

  assert.deepEqual(Object.keys(definitions).sort(), expectedExpressions);
  assert.equal(definitions['process.env.API_HOST'], JSON.stringify('https://gate.example.com'));
  assert.equal(definitions['import.meta.env.VITE_API_HOST'], JSON.stringify('https://vite-gate.example.com'));
  assert.equal(Object.values(definitions).filter((value) => value !== 'undefined').length, 2);
  assert.equal(Object.hasOwn(definitions, 'import.meta.env.VITE_UNRELATED_SECRET'), false);
  assert.equal(Object.hasOwn(definitions, 'process.env.UNRELATED_SECRET'), false);
});

test('public environment allowlists exactly match settings.js references', async () => {
  const { IMPORT_META_ENV_KEYS, PROCESS_ENV_KEYS } = await loadHelpers();
  const settingsSource = readFileSync(path.resolve(__dirname, '../src/settings.js'), 'utf8');
  const importMetaKeys = [
    ...new Set([...settingsSource.matchAll(/import\.meta\.env\.([A-Z0-9_]+)/g)].map((match) => match[1])),
  ].sort();
  const processKeys = [
    ...new Set([...settingsSource.matchAll(/process\.env\.([A-Z0-9_]+)/g)].map((match) => match[1])),
  ].sort();

  assert.deepEqual(IMPORT_META_ENV_KEYS, importMetaKeys);
  assert.deepEqual(PROCESS_ENV_KEYS, processKeys);
});

test('process-level neutral values isolate Vite config from ambient .env.local', { concurrency: false }, async () => {
  await withViteEnvironment({}, async () => {
    const fixtureRoot = mkdtempSync(path.join(os.tmpdir(), 'deck-vite-env-local-'));
    const fixtureConfigPath = path.join(fixtureRoot, 'vite.config.mjs');
    writeFileSync(
      path.join(fixtureRoot, '.env.local'),
      [
        'FIXTURE_ONLY=loaded-from-env-local',
        'DECK_HOST=0.0.0.0',
        'DECK_HTTPS=true',
        'DECK_PORT=9191',
        'SETTINGS_PATH=./src/settings-local.js',
        'SPINNAKER_ENV=ambient-output',
      ].join('\n'),
    );
    writeFileSync(
      fixtureConfigPath,
      [
        `import { loadEnv } from ${JSON.stringify(viteModuleUrl.href)};`,
        `import viteEnvironment from ${JSON.stringify(viteEnvironmentUrl.href)};`,
        `const root = ${JSON.stringify(fixtureRoot)};`,
        'const { mergeViteEnvironment } = viteEnvironment;',
        'export default ({ mode }) => {',
        '  const env = mergeViteEnvironment(loadEnv, mode, root, process.env);',
        '  return {',
        '    root,',
        '    define: Object.fromEntries(',
        "      ['FIXTURE_ONLY', 'DECK_HOST', 'DECK_HTTPS', 'DECK_PORT', 'SETTINGS_PATH', 'SPINNAKER_ENV'].map(",
        '        (key) => [`__${key}__`, JSON.stringify(env[key])],',
        '      ),',
        '    ),',
        '  };',
        '};',
      ].join('\n'),
    );

    try {
      const { resolveConfig } = await import('vite');
      const config = await resolveConfig({ configFile: fixtureConfigPath, logLevel: 'silent' }, 'serve');

      assert.equal(config.define.__FIXTURE_ONLY__, JSON.stringify('loaded-from-env-local'));
      for (const key of ['DECK_HOST', 'DECK_HTTPS', 'DECK_PORT', 'SETTINGS_PATH', 'SPINNAKER_ENV']) {
        assert.equal(config.define[`__${key}__`], JSON.stringify(NEUTRAL_VITE_ENV[key]), key);
      }
    } finally {
      rmSync(fixtureRoot, { recursive: true, force: true });
    }
  });
});

test('resolveBuildOutput keeps builds under the app dist directory', async () => {
  const { resolveBuildOutput } = await loadHelpers();

  assert.equal(resolveBuildOutput('/deck/packages/app', {}), '/deck/packages/app/dist');
  assert.equal(resolveBuildOutput('/deck/packages/app', { SPINNAKER_ENV: 'prod' }), '/deck/packages/app/dist/prod');
  assert.equal(
    resolveBuildOutput('/deck/packages/app', { SPINNAKER_ENV: 'prod/eu' }),
    '/deck/packages/app/dist/prod/eu',
  );
});

test('resolveBuildOutput rejects absolute and parent SPINNAKER_ENV paths', async () => {
  const { resolveBuildOutput } = await loadHelpers();
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
    assert.throws(() => resolveBuildOutput('/deck/packages/app', { SPINNAKER_ENV }), /Invalid SPINNAKER_ENV/),
  );
});

test('Vite server keeps secure host defaults and localhost HTTP or HTTPS support', () => {
  assert.doesNotMatch(viteConfigSource, /allowedHosts\s*:\s*true/);
  assert.match(viteConfigSource, /host:\s*env\.DECK_HOST\s*\|\|\s*['"]localhost['"]/);
});

test('Vite serves runtime settings as transformed modules', { concurrency: false }, async () => {
  await withViteEnvironment({}, async () => {
    let server;

    try {
      const { createServer } = await import('vite');
      server = await createServer({
        configFile: viteConfigPath,
        server: { middlewareMode: true },
      });
      const source = [
        '<script src="./settings.js" vite-ignore></script>',
        '<script src="./settings-local.js" vite-ignore></script>',
      ].join('\n');
      const html = await server.transformIndexHtml('/', source);

      assert.match(html, /<script type="module" src="\/@fs\/[^"]+\/settings\.js"><\/script>/);
      assert.match(html, /<script type="module" src="\/@fs\/[^"]+\/settings-local\.js"><\/script>/);
      assert.doesNotMatch(html, /<script src="\.\/settings(?:-local)?\.js"/);
    } finally {
      await server?.close();
    }
  });
});

test('Vite permits only the selected external settings file', { concurrency: false }, async () => {
  const externalRoot = mkdtempSync(path.join(os.tmpdir(), 'deck-vite-settings-'));
  const settingsPath = path.join(externalRoot, 'settings.js');
  const siblingPath = path.join(externalRoot, 'sibling-secret.js');
  writeFileSync(settingsPath, 'window.selectedSettings = true;');
  writeFileSync(siblingPath, 'window.secret = "must-not-leak";');

  try {
    await withViteEnvironment(
      {
        DECK_CERT: '',
        DECK_HTTPS: 'false',
        DECK_KEY: '',
        SETTINGS_PATH: settingsPath,
      },
      async () => {
        const server = await startConfiguredViteServer();
        try {
          const origin = serverOrigin(server);
          const selectedResponse = await fetch(`${origin}${fsModuleUrl(settingsPath)}`);
          const selectedSource = await selectedResponse.text();
          const siblingResponse = await fetch(`${origin}${fsModuleUrl(siblingPath)}`);
          const siblingSource = await siblingResponse.text();

          assert.equal(selectedResponse.status, 200);
          assert.match(selectedSource, /selectedSettings/);
          assert.equal(siblingResponse.status, 403);
          assert.doesNotMatch(siblingSource, /must-not-leak/);
        } finally {
          await server.close();
        }
      },
    );
  } finally {
    rmSync(externalRoot, { recursive: true, force: true });
  }
});

test('Vite serves default app settings through the runtime module URL', { concurrency: false }, async () => {
  await withViteEnvironment(
    {
      DECK_CERT: '',
      DECK_HTTPS: 'false',
      DECK_KEY: '',
      SETTINGS_PATH: '',
    },
    async () => {
      const server = await startConfiguredViteServer();
      try {
        const response = await fetch(`${serverOrigin(server)}${fsModuleUrl(path.join(appRoot, 'src/settings.js'))}`);

        assert.equal(response.status, 200);
        assert.match(await response.text(), /spinnakerSettings/);
      } finally {
        await server.close();
      }
    },
  );
});

test('Vite does not substitute unapproved import.meta.env or process.env secrets', { concurrency: false }, async () => {
  const externalRoot = mkdtempSync(path.join(os.tmpdir(), 'deck-vite-env-'));
  const settingsPath = path.join(externalRoot, 'settings.js');
  writeFileSync(
    settingsPath,
    [
      'globalThis.boundaryValues = [',
      '  import.meta.env.DECK_INTERNAL_NO_PUBLIC_ENV_SECRET,',
      '  import.meta.env.VITE_UNRELATED_SECRET,',
      '  process.env.UNRELATED_SECRET,',
      '];',
    ].join('\n'),
  );

  try {
    await withViteEnvironment(
      {
        DECK_INTERNAL_NO_PUBLIC_ENV_SECRET: 'must-not-leak',
        SETTINGS_PATH: settingsPath,
        UNRELATED_SECRET: 'must-not-leak',
        VITE_UNRELATED_SECRET: 'must-not-leak',
      },
      async () => {
        const { createServer } = await import('vite');
        const server = await createServer({
          configFile: viteConfigPath,
          logLevel: 'silent',
          server: { middlewareMode: true },
        });
        try {
          const transformed = await server.transformRequest(fsModuleUrl(settingsPath));

          assert.ok(transformed);
          assert.doesNotMatch(transformed.code, /must-not-leak/);
        } finally {
          await server.close();
        }
      },
    );
  } finally {
    rmSync(externalRoot, { recursive: true, force: true });
  }
});

test('static copy targets select favicon by command and expose root version in serve', async () => {
  const { staticCopyTargets } = await loadHelpers();
  const appRoot = '/deck/packages/app';
  const deckRoot = '/deck';
  const styleguideRoot = '/styleguide';

  const buildTargets = staticCopyTargets(appRoot, deckRoot, styleguideRoot, 'build');
  const serveTargets = staticCopyTargets(appRoot, deckRoot, styleguideRoot, 'serve');
  const versionTarget = buildTargets.find((target) => target.src === '/deck/version.json');

  assert.equal(buildTargets.at(-1).src, '/deck/packages/app/icons/prod-favicon.ico');
  assert.equal(serveTargets.at(-1).src, '/deck/packages/app/icons/dev-favicon.ico');
  assert.deepEqual(versionTarget, {
    src: '/deck/version.json',
    dest: 'deck-root',
    rename: 'version.json',
  });
});

test('resolveSettingsPath resolves defaults and relative overrides from the app root', async () => {
  const { resolveSettingsPath } = await loadHelpers();

  assert.equal(resolveSettingsPath('/deck/packages/app', {}), '/deck/packages/app/src/settings.js');
  assert.equal(
    resolveSettingsPath('/deck/packages/app', { SETTINGS_PATH: './config/settings.js' }),
    '/deck/packages/app/config/settings.js',
  );
  assert.equal(resolveSettingsPath('/deck/packages/app', { SETTINGS_PATH: '/tmp/settings.js' }), '/tmp/settings.js');
});

test('getTlsMode distinguishes HTTP, generated certificates, and valid custom certificates', async () => {
  const { getTlsMode } = await loadHelpers();

  assert.deepEqual(getTlsMode({ DECK_HTTPS: 'false' }), { kind: 'http' });
  assert.deepEqual(getTlsMode({ DECK_HTTPS: 'true' }), { kind: 'generated' });
  assert.deepEqual(getTlsMode({ DECK_CERT: '/cert.pem', DECK_KEY: '/key.pem', DECK_CA_CERT: '/ca.pem' }), {
    kind: 'custom',
    cert: '/cert.pem',
    key: '/key.pem',
    ca: '/ca.pem',
  });
  assert.deepEqual(getTlsMode({ DECK_CERT: '/cert.pem', DECK_KEY: '/key.pem' }), {
    kind: 'custom',
    cert: '/cert.pem',
    key: '/key.pem',
    ca: undefined,
  });
  assert.throws(
    () => getTlsMode({ DECK_CERT: '/cert.pem' }),
    new Error('DECK_CERT and DECK_KEY must be provided together'),
  );
  assert.throws(
    () => getTlsMode({ DECK_KEY: '/key.pem' }),
    new Error('DECK_CERT and DECK_KEY must be provided together'),
  );
});

const assert = require('node:assert/strict');
const { chmodSync, existsSync, mkdtempSync, mkdirSync, readFileSync, rmSync, writeFileSync } = require('node:fs');
const { createRequire } = require('node:module');
const { tmpdir } = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const test = require('node:test');

const deckRoot = path.resolve(__dirname, '../../..');
const repositoryRoot = path.resolve(deckRoot, '..');
const pullScript = path.join(repositoryRoot, 'pull.sh');
const standaloneDeckKayentaRoot = path.join(repositoryRoot, 'deck-kayenta');
const parseYaml = createRequire(path.join(deckRoot, 'packages/core/package.json'))('js-yaml').load;
const protectedDeckPaths = ['deck/**'];
const deckWorkflowPaths = [...protectedDeckPaths, '.github/workflows/deck.yml'];
const operationalFiles = [
  'settings.gradle',
  'build.gradle',
  '.github/dependencies.yml',
  '.github/workflows/deck.yml',
  'deck/build.gradle',
  'deck/pnpm-workspace.yaml',
  'deck/packages/app/webpack.config.js',
];
const standaloneDeckKayentaPath = String.raw`(?:\.\.?/)*deck-kayenta(?:/[A-Za-z0-9_.*@+-]+)*`;
const activeStandaloneDeckKayentaPatterns = [
  new RegExp(`['"]${standaloneDeckKayentaPath}['"]`),
  /^\s*deck-kayenta\s*:/,
  /(?:^|[\s:[,{])[&*]deck-kayenta(?=[\s:,\]}]|$)/,
  /(?:^|[\s=(,:[])\.\.?\/deck-kayenta(?:\/[A-Za-z0-9_.*@+-]+)*(?=[\s'"),\]}]|$)/,
  new RegExp(`^\\s*-\\s*${standaloneDeckKayentaPath}\\s*$`),
  new RegExp(`:\\s*${standaloneDeckKayentaPath}\\s*(?:[,}\\]])?$`),
];

function loadYaml(relativePath) {
  return parseYaml(readFileSync(path.join(repositoryRoot, relativePath), 'utf8'));
}

function activeStandaloneDeckKayentaReferences(source) {
  return source.split(/\r?\n/).flatMap((line, index) => {
    const trimmedLine = line.trim();
    if (trimmedLine.startsWith('#') || trimmedLine.startsWith('//')) return [];

    const activeLine = line.replace(/\s+(?:#|\/\/).*$/, '');
    return activeStandaloneDeckKayentaPatterns.some((pattern) => pattern.test(activeLine)) ? [index + 1] : [];
  });
}

function capturePull(repo) {
  const fixtureRoot = mkdtempSync(path.join(tmpdir(), 'kayenta-pull-'));
  const binRoot = path.join(fixtureRoot, 'bin');
  const gitLog = path.join(fixtureRoot, 'git.log');
  mkdirSync(binRoot);
  const gitStub = path.join(binRoot, 'git');
  writeFileSync(
    gitStub,
    [
      '#!/usr/bin/env node',
      "const { appendFileSync } = require('node:fs');",
      "appendFileSync(process.env.PULL_GIT_LOG, JSON.stringify({ prefix: process.env.GIT_SUBTREE, remote: process.env.GIT_SUBTREE_REMOTE, args: process.argv.slice(2) }) + '\\n');",
    ].join('\n'),
  );
  chmodSync(gitStub, 0o755);

  try {
    const result = spawnSync('bash', [pullScript, repo], {
      cwd: fixtureRoot,
      encoding: 'utf8',
      env: {
        ...process.env,
        PATH: `${binRoot}${path.delimiter}${process.env.PATH}`,
        PULL_GIT_LOG: gitLog,
      },
    });
    assert.equal(result.status, 0, result.stderr);
    return readFileSync(gitLog, 'utf8')
      .trim()
      .split('\n')
      .map((line) => JSON.parse(line));
  } finally {
    rmSync(fixtureRoot, { recursive: true, force: true });
  }
}

test('deck-kayenta pulls target the nested Deck package', () => {
  const calls = capturePull('deck-kayenta');

  assert.equal(calls.length, 2);
  calls.forEach(({ prefix, remote }) => {
    assert.equal(prefix, 'deck/packages/kayenta');
    assert.equal(remote, 'github.com:spinnaker/deck-kayenta.git');
  });
  assert.deepEqual(calls[0].args, ['fetch', 'git@github.com:spinnaker/deck-kayenta.git', 'master']);
  assert.deepEqual(calls[1].args, [
    'merge',
    '--edit',
    '--strategy',
    'subtree',
    '-X',
    'subtree=deck/packages/kayenta',
    'FETCH_HEAD',
  ]);
});

test('standalone Deck Kayenta directory is absent', () => {
  assert.equal(existsSync(standaloneDeckKayentaRoot), false);
});

test('other repository pulls retain their repository-name prefix', () => {
  const calls = capturePull('deck');

  assert.equal(calls.length, 2);
  calls.forEach(({ prefix, remote }) => {
    assert.equal(prefix, 'deck');
    assert.equal(remote, 'github.com:spinnaker/deck.git');
  });
  assert.deepEqual(calls[1].args, ['merge', '--edit', '--strategy', 'subtree', '-X', 'subtree=deck', 'FETCH_HEAD']);
});

test('active build and workflow configuration has no standalone Deck Kayenta references', () => {
  const staleReferences = operationalFiles.flatMap((file) =>
    activeStandaloneDeckKayentaReferences(readFileSync(path.join(repositoryRoot, file), 'utf8')).map(
      (line) => `${file}:${line}`,
    ),
  );
  assert.deepEqual(staleReferences, []);
});

test('standalone path guard ignores comments and detects active top-level paths', () => {
  const source = [
    "# Removed path: 'deck-kayenta/**'",
    "// Removed includeBuild 'deck-kayenta'",
    "includeBuild 'deck-kayenta'",
    "  - 'deck-kayenta/**' # stale workflow path",
    'deck-kayenta: &deck-kayenta',
    '- *deck-kayenta',
    '../deck-kayenta/build.gradle',
    'description: deck-kayenta was removed after consolidation',
    'path: deck/packages/kayenta',
  ].join('\n');

  assert.deepEqual(activeStandaloneDeckKayentaReferences(source), [3, 4, 5, 6, 7]);
});

test('Deck workflow and dependency ownership remains scoped to the Deck tree', () => {
  const deckWorkflow = loadYaml('.github/workflows/deck.yml');
  const dependencies = loadYaml('.github/dependencies.yml');

  assert.deepEqual(deckWorkflow.on.pull_request.paths, deckWorkflowPaths);
  assert.deepEqual(deckWorkflow.on.push.paths, deckWorkflowPaths);
  assert.deepEqual(dependencies.deck, protectedDeckPaths);
});

const assert = require('node:assert/strict');
const { existsSync, readFileSync } = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const appRoot = path.resolve(__dirname, '..');
const coreSourceRoot = path.resolve(appRoot, '../core/src');

const readAppFile = (relativePath) => readFileSync(path.join(appRoot, relativePath), 'utf8');
const readCoreFile = (relativePath) => readFileSync(path.join(coreSourceRoot, relativePath), 'utf8');

test('index.html exposes the React root', () => {
  const html = readAppFile('index.html');

  assert.match(html, /<html class="no-js">/);
  assert.match(html, /<div id="spinnaker-root"><\/div>/);
});

test('index.deck template exposes the same React root contract as index.html', () => {
  const html = readAppFile('index.deck');

  assert.match(html, /<html class="no-js">/);
  assert.match(html, /<div id="spinnaker-root"><\/div>/);
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
  const webpackConfig = readAppFile('webpack.config.js');

  assert.doesNotMatch(appEntry, /import ['"]\.\/settings(?:\.js)?['"];?/);
  assert.match(webpackConfig, /const SETTINGS_PATH = process\.env\.SETTINGS_PATH \|\| '\.\/src\/settings\.js';/);
  assert.match(webpackConfig, /settings: SETTINGS_PATH/);
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

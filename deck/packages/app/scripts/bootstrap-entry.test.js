const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const appRoot = path.resolve(__dirname, '..');

const readAppFile = (relativePath) => readFileSync(path.join(appRoot, relativePath), 'utf8');

test('index.html exposes a React root instead of AngularJS document bootstrap', () => {
  const html = readAppFile('index.html');

  assert.match(html, /<html class="no-js">/);
  assert.match(html, /<div id="spinnaker-root"><\/div>/);
  assert.doesNotMatch(html, /ng-app=/);
  assert.doesNotMatch(html, /ng-strict-di=/);
  assert.doesNotMatch(html, /<spinnaker\b/);
});

test('index.deck template exposes the same React root contract as index.html', () => {
  const html = readAppFile('index.deck');

  assert.match(html, /<html class="no-js">/);
  assert.match(html, /<div id="spinnaker-root"><\/div>/);
  assert.doesNotMatch(html, /ng-app=/);
  assert.doesNotMatch(html, /ng-strict-di=/);
  assert.doesNotMatch(html, /<spinnaker\b/);
});

test('app entry renders Deck directly without creating an AngularJS root module', () => {
  const appEntry = readAppFile('src/app.ts');

  assert.match(appEntry, /import \{[^}]*\bbootstrapDeck\b[^}]*\} from '@spinnaker\/core';/);
  assert.match(
    appEntry,
    /void bootstrapDeck\(document\.getElementById\('spinnaker-root'\)\)\.catch\(\(error\) => \{\s*console\.error\('Deck bootstrap failed', error\);\s*\}\);/,
  );
  assert.doesNotMatch(appEntry, /angular\.bootstrap/);
  assert.doesNotMatch(appEntry, /from 'angular'/);
  assert.doesNotMatch(appEntry, /module\('netflix\.spinnaker'/);
  assert.doesNotMatch(appEntry, /strictDi/);
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

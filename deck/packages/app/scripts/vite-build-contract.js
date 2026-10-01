const assert = require('node:assert/strict');
const { existsSync, readFileSync, readdirSync, statSync } = require('node:fs');
const path = require('node:path');
const { SourceMapConsumer } = require('source-map');
const { mergeViteEnvironment } = require('./vite-environment');
const { resolveBuildOutput } = require('./vite-output-path');

const FIXED_FILES = [
  'index.html',
  'settings.js',
  'settings-local.js',
  'plugin-manifest.json',
  'styleguide.html',
  'favicon.ico',
  'version.json',
];
const TEMPLATE_TOKEN_PATTERN = /\{%[\s\S]*?%\}/g;
const SOURCE_MAP_REFERENCE_PATTERN = /\/\/[#@]\s*sourceMappingURL=([^\s]+)/g;
const USAGE = 'Usage: vite-build-contract.js [--] [output-dir [version-json [settings-source]]]';

function listFiles(directory) {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const entryPath = path.join(directory, entry.name);
    return entry.isDirectory() ? listFiles(entryPath) : [entryPath];
  });
}

function relativeArtifactPath(root, filePath) {
  return path.relative(root, filePath).split(path.sep).join('/');
}

function normalizeLocalReference(reference) {
  return reference.split(/[?#]/, 1)[0];
}

function resolveLocalArtifact(root, reference, description) {
  const cleanReference = normalizeLocalReference(reference);
  const relativeReference = cleanReference.replace(/^\.?[\\/]/, '');
  const artifactPath = path.resolve(root, relativeReference);
  const relativePath = path.relative(root, artifactPath);

  assert.ok(
    relativePath !== '..' && !relativePath.startsWith(`..${path.sep}`) && !path.isAbsolute(relativePath),
    `${description} escapes the build output: ${reference}`,
  );
  return artifactPath;
}

function validateSourceMap(mapPath, mapSource) {
  const relativeMapPath = path.basename(mapPath);
  assert.doesNotMatch(
    mapSource,
    /must-not-leak/,
    `Secret sentinel must-not-leak was emitted in source map ${relativeMapPath}`,
  );
  assert.doesNotMatch(
    mapSource,
    /__vite-browser-external|Transform\s*=\s*stream\.Transform/,
    `Vite browser external was emitted in ${mapPath}`,
  );

  let sourceMap;
  try {
    sourceMap = JSON.parse(mapSource);
  } catch (error) {
    throw new Error(`Invalid source map JSON in ${relativeMapPath}: ${error.message}`);
  }

  const validShape =
    sourceMap &&
    !Array.isArray(sourceMap) &&
    sourceMap.version === 3 &&
    Array.isArray(sourceMap.sources) &&
    sourceMap.sources.every((source) => typeof source === 'string') &&
    Array.isArray(sourceMap.names) &&
    sourceMap.names.every((name) => typeof name === 'string') &&
    typeof sourceMap.mappings === 'string' &&
    (sourceMap.sourcesContent === undefined ||
      (Array.isArray(sourceMap.sourcesContent) && sourceMap.sourcesContent.length === sourceMap.sources.length));
  assert.ok(validShape, `Invalid source map v3 essentials in ${relativeMapPath}`);

  assert.doesNotMatch(
    JSON.stringify([sourceMap.sources, sourceMap.sourcesContent || []]),
    /must-not-leak/,
    `Secret sentinel must-not-leak was emitted in source map ${relativeMapPath}`,
  );

  try {
    const consumer = new SourceMapConsumer(sourceMap);
    consumer.eachMapping(() => {});
  } catch (error) {
    throw new Error(`Invalid source map mappings in ${relativeMapPath}: ${error.message}`);
  }
}

function verifyJavaScriptArtifacts(outputDir, emittedFiles) {
  const javaScriptFiles = new Set(
    emittedFiles.filter((emittedFile) => emittedFile.endsWith('.js')).map((filePath) => path.resolve(filePath)),
  );
  const sourceMapFiles = new Set(
    emittedFiles.filter((emittedFile) => emittedFile.endsWith('.js.map')).map((filePath) => path.resolve(filePath)),
  );

  for (const mapPath of sourceMapFiles) {
    const relativeMapPath = relativeArtifactPath(outputDir, mapPath);
    const javaScriptPath = mapPath.slice(0, -'.map'.length);
    assert.ok(
      javaScriptFiles.has(javaScriptPath) && statSync(javaScriptPath).isFile(),
      `Orphan source map without corresponding JavaScript artifact: ${relativeMapPath}`,
    );
    validateSourceMap(mapPath, readFileSync(mapPath, 'utf8'));
  }

  for (const filePath of javaScriptFiles) {
    const relativeJsPath = relativeArtifactPath(outputDir, filePath);
    const javaScript = readFileSync(filePath, 'utf8');
    assert.doesNotMatch(javaScript, /must-not-leak/, `Secret sentinel must-not-leak was emitted in ${relativeJsPath}`);
    assert.doesNotMatch(
      javaScript,
      /__vite-browser-external|Transform\s*=\s*stream\.Transform/,
      `Vite browser external was emitted in ${relativeJsPath}`,
    );

    const expectedMapPath = `${filePath}.map`;
    assert.ok(sourceMapFiles.has(expectedMapPath), `Missing source map for JavaScript artifact: ${relativeJsPath}`);
    const references = [...javaScript.matchAll(SOURCE_MAP_REFERENCE_PATTERN)];
    assert.ok(references.length > 0, `Missing sourceMappingURL in JavaScript artifact: ${relativeJsPath}`);

    const mapReference = references.at(-1)[1];
    assert.doesNotMatch(mapReference, /^(?:data:|https?:)/i, `Source map reference must be local: ${relativeJsPath}`);
    const referencedMapPath = resolveLocalArtifact(path.dirname(filePath), mapReference, 'Source map reference');
    assert.ok(
      existsSync(referencedMapPath),
      `Referenced source map does not exist for ${relativeJsPath}: ${mapReference}`,
    );
    assert.equal(
      referencedMapPath,
      expectedMapPath,
      `sourceMappingURL for ${relativeJsPath} must reference ${path.basename(expectedMapPath)}`,
    );
  }

  return javaScriptFiles;
}

function verifyFileCopy(outputDir, relativePath, sourcePath) {
  const outputPath = path.join(outputDir, relativePath);
  assert.ok(existsSync(outputPath), `Missing copied artifact ${relativePath} from source ${sourcePath}`);
  assert.deepEqual(
    readFileSync(outputPath),
    readFileSync(sourcePath),
    `Copied artifact ${relativePath} differs from source ${sourcePath}`,
  );
}

function verifySourceArtifacts(outputDir, sources) {
  const fixedCopies = [
    ['plugin-manifest.json', sources.pluginManifestPath],
    ['version.json', sources.versionPath],
    ['styleguide.html', sources.styleguidePath],
    ['favicon.ico', sources.faviconPath],
  ];
  fixedCopies.forEach(([relativePath, sourcePath]) => verifyFileCopy(outputDir, relativePath, sourcePath));
}

function templateTokenCounts(source) {
  return (source.match(TEMPLATE_TOKEN_PATTERN) || []).reduce(
    (counts, token) => counts.set(token, (counts.get(token) || 0) + 1),
    new Map(),
  );
}

function verifyTemplateTokens(outputDir, templateSourcePath) {
  const expectedCounts = templateTokenCounts(readFileSync(templateSourcePath, 'utf8'));
  const actualCounts = templateTokenCounts(readFileSync(path.join(outputDir, 'settings.js'), 'utf8'));

  for (const [token, expectedCount] of expectedCounts) {
    assert.ok(
      (actualCounts.get(token) || 0) >= expectedCount,
      `Missing template token occurrence in settings.js: ${token}`,
    );
  }
  assert.deepEqual(
    actualCounts,
    expectedCounts,
    'Emitted settings.js template tokens do not match the source template',
  );
}

function verifyDeckBuild(outputDir, expectedVersionPath, templateSourcePath) {
  for (const relativePath of FIXED_FILES) {
    assert.ok(existsSync(path.join(outputDir, relativePath)), `Missing required build artifact: ${relativePath}`);
  }

  const html = readFileSync(path.join(outputDir, 'index.html'), 'utf8');
  const scripts = [...html.matchAll(/<script\b([^>]*)>/gi)].map((match) => {
    const attributes = match[1];
    const source = attributes.match(/\bsrc=["']([^"']+)["']/i)?.[1];
    return { attributes, source, normalizedSource: source && normalizeLocalReference(source) };
  });
  for (const script of scripts) {
    const { source } = script;
    if (!source || /^https?:\/\//i.test(source)) {
      continue;
    }
    const scriptPath = resolveLocalArtifact(outputDir, source, 'Local script reference');
    assert.ok(
      existsSync(scriptPath),
      `Missing local script referenced by index.html: ${relativeArtifactPath(outputDir, scriptPath)}`,
    );
    assert.ok(
      statSync(scriptPath).isFile(),
      `Local script target must be a regular file: ${relativeArtifactPath(outputDir, scriptPath)}`,
    );
    script.localPath = scriptPath;
  }
  const settingsIndex = scripts.findIndex(({ normalizedSource }) => normalizedSource === './settings.js');
  const localSettingsIndex = scripts.findIndex(({ normalizedSource }) => normalizedSource === './settings-local.js');
  const assetsDir = path.resolve(outputDir, 'assets');
  const appModuleIndex = scripts.findIndex(({ attributes, normalizedSource, localPath }) => {
    if (!/\btype=["']module["']/i.test(attributes) || !normalizedSource?.startsWith('./assets/')) {
      return false;
    }
    const relativeAppPath = localPath && path.relative(assetsDir, localPath);
    return (
      path.extname(normalizedSource).toLowerCase() === '.js' &&
      relativeAppPath !== undefined &&
      relativeAppPath !== '..' &&
      !relativeAppPath.startsWith(`..${path.sep}`) &&
      !path.isAbsolute(relativeAppPath)
    );
  });

  assert.ok(
    settingsIndex !== -1 && localSettingsIndex > settingsIndex && appModuleIndex > localSettingsIndex,
    'index.html must load settings.js, then settings-local.js, then the app module under ./assets/ as a local .js file',
  );

  const emittedFiles = listFiles(outputDir);
  const validatedJavaScript = verifyJavaScriptArtifacts(outputDir, emittedFiles);
  assert.ok(
    validatedJavaScript.has(path.resolve(scripts[appModuleIndex].localPath)),
    `Application module did not participate in JavaScript validation: ${scripts[appModuleIndex].normalizedSource}`,
  );

  if (expectedVersionPath) {
    const actualVersion = JSON.parse(readFileSync(path.join(outputDir, 'version.json'), 'utf8'));
    const expectedVersion = JSON.parse(readFileSync(expectedVersionPath, 'utf8'));
    assert.deepEqual(actualVersion, expectedVersion, 'Output version.json does not match the expected version.json');
  }
  if (templateSourcePath) {
    verifyTemplateTokens(outputDir, templateSourcePath);
  }
}

function parseCliArguments(argv, defaults) {
  const args = [...argv];
  if (args[0] === '--') {
    args.shift();
  }
  if (args.length > 3 || args.some((argument) => !argument || argument === '--')) {
    throw new Error(USAGE);
  }

  const templateSourcePath = args[2] || defaults.templateSourcePath;
  return {
    outputDir: path.resolve(args[0] || defaults.outputDir),
    expectedVersionPath: path.resolve(args[1] || defaults.expectedVersionPath),
    templateSourcePath: templateSourcePath ? path.resolve(templateSourcePath) : undefined,
  };
}

function buildCliArguments(argv, appRoot, deckRoot, env) {
  const args = argv[0] === '--' ? argv.slice(1) : argv;
  return parseCliArguments(argv, {
    outputDir: args[0] || resolveBuildOutput(appRoot, env),
    expectedVersionPath: path.join(deckRoot, 'version.json'),
    templateSourcePath: path.resolve(appRoot, env.SETTINGS_PATH || './src/settings.js'),
  });
}

function repositoryArtifactSources(deckRoot) {
  const appRoot = path.join(deckRoot, 'packages/app');
  const styleguidePackage = require.resolve('@spinnaker/styleguide/package.json', { paths: [appRoot] });

  return {
    pluginManifestPath: path.join(appRoot, 'public/plugin-manifest.json'),
    versionPath: path.join(deckRoot, 'version.json'),
    styleguidePath: path.join(path.dirname(styleguidePackage), 'public/styleguide.html'),
    faviconPath: path.join(appRoot, 'icons/prod-favicon.ico'),
  };
}

async function main() {
  const appRoot = path.resolve(__dirname, '..');
  const deckRoot = path.resolve(appRoot, '../..');

  try {
    const { loadEnv } = await import('vite');
    const env = mergeViteEnvironment(loadEnv, 'production', appRoot, process.env);
    const cliArguments = buildCliArguments(process.argv.slice(2), appRoot, deckRoot, env);
    verifyDeckBuild(cliArguments.outputDir, cliArguments.expectedVersionPath, cliArguments.templateSourcePath);
    verifySourceArtifacts(cliArguments.outputDir, repositoryArtifactSources(deckRoot));
    process.stdout.write(`Verified Deck Vite build: ${cliArguments.outputDir}\n`);
  } catch (error) {
    process.stderr.write(`${error instanceof Error ? error.message : String(error)}\n`);
    process.exitCode = 1;
  }
}

if (require.main === module) {
  void main();
}

module.exports = { buildCliArguments, parseCliArguments, verifyDeckBuild, verifySourceArtifacts, verifyTemplateTokens };

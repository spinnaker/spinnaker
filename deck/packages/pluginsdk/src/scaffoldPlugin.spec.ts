import { execFileSync } from 'node:child_process';
import { cpSync, mkdirSync, mkdtempSync, readdirSync, readFileSync, rmSync, symlinkSync, writeFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

import { PluginRegistry, Registry } from '@spinnaker/core';
import { sharedLibraries } from '../../core/src/plugins/sharedLibraries';

const require = createRequire(import.meta.url);
const PLUGINSDK_ROOT = path.resolve(__dirname, '..');
const SCAFFOLD_ROOT = path.join(PLUGINSDK_ROOT, 'scaffold');

// Matches the specifier of every static import/export and dynamic import in an ES module bundle.
const MODULE_SPECIFIER = /(?:\bfrom\s*|\bimport\s*\(\s*|\bimport\s+)(["'])([^"']+)\1/g;

function bareSpecifiers(source: string): string[] {
  return [...source.matchAll(MODULE_SPECIFIER)]
    .map(([, , specifier]) => specifier)
    .filter((specifier) => !/^(?:\.{1,2}\/|\/|data:|file:|https?:)/.test(specifier));
}

describe('plugin scaffold build', () => {
  let workspace: string;
  let distDir: string;

  beforeAll(async () => {
    // Vitest only loads modules from inside the Deck workspace, so keep the fixture under node_modules/.cache.
    const cacheRoot = path.resolve(PLUGINSDK_ROOT, '../../node_modules/.cache');
    mkdirSync(cacheRoot, { recursive: true });
    workspace = mkdtempSync(path.join(cacheRoot, 'plugin-scaffold-spec-'));
    const pluginRoot = path.join(workspace, 'plugin');
    mkdirSync(pluginRoot);
    cpSync(path.join(SCAFFOLD_ROOT, 'src'), path.join(pluginRoot, 'src'), { recursive: true });

    // A non-shared runtime dependency and a non-shared subpath of a shared peer dependency. Both
    // must end up in the bundle rather than as bare imports.
    const dependencyRoot = path.join(pluginRoot, 'node_modules', 'plugin-only-dependency');
    mkdirSync(dependencyRoot, { recursive: true });
    writeFileSync(
      path.join(dependencyRoot, 'package.json'),
      JSON.stringify({ name: 'plugin-only-dependency', version: '1.0.0', type: 'module', main: 'index.js' }),
    );
    writeFileSync(path.join(dependencyRoot, 'index.js'), "export const pluginOnly = () => 'bundled dependency';\n");
    const rxjsRoot = path.dirname(
      require.resolve('rxjs/package.json', { paths: [path.resolve(PLUGINSDK_ROOT, '../core')] }),
    );
    symlinkSync(rxjsRoot, path.join(pluginRoot, 'node_modules', 'rxjs'), 'dir');

    const scaffoldPackage = JSON.parse(readFileSync(path.join(SCAFFOLD_ROOT, 'package.json'), 'utf8'));
    writeFileSync(
      path.join(pluginRoot, 'package.json'),
      JSON.stringify({
        ...scaffoldPackage,
        dependencies: { ...scaffoldPackage.dependencies, 'plugin-only-dependency': '1.0.0' },
        peerDependencies: { rxjs: '*' },
      }),
    );
    writeFileSync(
      path.join(pluginRoot, 'src', 'index.ts'),
      `${readFileSync(path.join(pluginRoot, 'src', 'index.ts'), 'utf8')}
import { pluginOnly } from 'plugin-only-dependency';
import { of } from 'rxjs';
import { map } from 'rxjs/operators';

export const extras = { pluginOnly, mapped: () => new Promise((resolve) => of(1).pipe(map((value) => value + 1)).subscribe(resolve)) };
`,
    );

    // Build in a separate Node process, as `spinnaker-scripts build` would, outside Vitest's module graph.
    const buildScript = path.join(workspace, 'build.cjs');
    writeFileSync(
      buildScript,
      `const { createRequire } = require('node:module');
const { pathToFileURL } = require('node:url');
const { createPluginViteConfig } = require(${JSON.stringify(require.resolve('../pluginconfig/vite.config.js'))});
const scriptsRequire = createRequire(${JSON.stringify(require.resolve('@spinnaker/scripts/package.json'))});
import(pathToFileURL(scriptsRequire.resolve('vite')).href)
  .then(async ({ build }) => build({ ...(await createPluginViteConfig(process.argv[2])), configFile: false, logLevel: 'warn' }))
  .catch((error) => {
    console.error(error);
    process.exit(1);
  });
`,
    );
    execFileSync(process.execPath, [buildScript, pluginRoot], { env: { ...process.env, NODE_ENV: 'production' } });

    // Load the bundle away from the plugin's own node_modules, as Deck does when it fetches a plugin URL.
    distDir = path.join(workspace, 'served');
    cpSync(path.join(pluginRoot, 'build', 'dist'), distDir, { recursive: true });
  }, 120000);

  afterAll(() => {
    rmSync(workspace, { recursive: true, force: true });
  });

  it('bundles non-shared dependencies and leaves no bare imports', () => {
    const bundles = readdirSync(distDir).filter((file) => file.endsWith('.js'));
    const source = bundles.map((file) => readFileSync(path.join(distDir, file), 'utf8')).join('\n');

    expect(bundles).toContain('index.js');
    expect(bareSpecifiers(source)).toEqual([]);
    expect(source).toContain('bundled dependency');
    expect(source).toContain('spinnaker.plugins.sharedLibraries._spinnaker_core');
    expect(source).toContain('spinnaker.plugins.sharedLibraries.react');
  });

  it('loads in Deck through the plugin registry and registers its extensions', async () => {
    const globals = { plugins: { sharedLibraries: {} } };
    (window as any).spinnaker = globals;
    (globalThis as any).spinnaker = globals;
    await sharedLibraries.exposeSharedLibraries();

    const registry = new PluginRegistry();
    await registry.loadPluginManifest(
      'deck',
      'test',
      Promise.resolve([{ id: 'scaffold', version: '1.0.0', url: path.join(distDir, 'index.js') }]),
    );

    const [module] = await registry.loadPlugins();

    expect(module?.plugin.stages?.map((stage) => stage.key)).toEqual(['widgetize']);
    expect(Registry.pipeline.getStageConfig({ type: 'widgetize' } as any)?.label).toBe('Widgetize');
    expect((module as any).extras.pluginOnly()).toBe('bundled dependency');
    await expect((module as any).extras.mapped()).resolves.toBe(2);
  });
});

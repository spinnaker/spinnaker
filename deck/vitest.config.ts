import { createRequire } from 'node:module';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { defineConfig } from 'vitest/config';

const DECK_ROOT = path.dirname(fileURLToPath(import.meta.url));
const MODULES_ROOT = path.resolve(DECK_ROOT, 'packages');
const require = createRequire(import.meta.url);
const CORE_PACKAGE_ROOT = path.dirname(require.resolve('@spinnaker/core/package.json', { paths: [MODULES_ROOT] }));

// Dedupe the singleton packages, resolved from core, so every workspace package shares one copy.
// String `find` (not regex) so subpaths like `react-dom/test-utils` are rewritten too.
const SHARED_PACKAGES = ['@uirouter/core', '@uirouter/react', 'react', 'react-dom'];
const sharedAlias = SHARED_PACKAGES.map((name) => ({
  find: name,
  replacement: path.dirname(require.resolve(`${name}/package.json`, { paths: [CORE_PACKAGE_ROOT] })),
}));

const REACT_VIRTUALIZED_COMMONJS = require.resolve('react-virtualized/dist/commonjs/index.js', {
  paths: [CORE_PACKAGE_ROOT],
});

// Source aliases: bare + @spinnaker/<pkg> -> <pkg>/src, so specs resolve against package sources.
const PROVIDER_PACKAGES = [
  'amazon',
  'appengine',
  'azure',
  'cloudfoundry',
  'cloudrun',
  'core',
  'dcos',
  'docker',
  'ecs',
  'google',
  'huaweicloud',
  'kubernetes',
  'mocks',
  'oracle',
  'tencentcloud',
];
const providerAlias = PROVIDER_PACKAGES.flatMap((pkg) => {
  const src = path.resolve(MODULES_ROOT, `${pkg}/src`);
  return [
    { find: `@spinnaker/${pkg}`, replacement: src },
    { find: pkg, replacement: src },
  ];
});

export default defineConfig({
  // Match the package builds (es2019 => useDefineForClassFields:false) so legacy
  // experimentalDecorators (e.g. lodash-decorators @Debounce/@BindAll) emit as they do in dist.
  esbuild: {
    target: 'es2019',
  },
  resolve: {
    alias: [
      ...sharedAlias,
      { find: /^react-virtualized$/, replacement: REACT_VIRTUALIZED_COMMONJS },
      {
        find: 'coreImports',
        replacement: path.resolve(MODULES_ROOT, 'core/src/presentation/less/imports/commonImports.less'),
      },
      { find: 'root', replacement: DECK_ROOT },
      ...providerAlias,
    ],
    dedupe: SHARED_PACKAGES,
    extensions: ['.json', '.ts', '.tsx', '.js', '.jsx', '.css', '.less'],
    // Match the app build so workspace packages exposing only `module` (e.g. @spinnaker/presentation) resolve.
    mainFields: ['browser', 'module', 'jsnext:main', 'jsnext', 'main:esnext', 'main'],
  },
  test: {
    globals: true,
    environment: 'jsdom',
    // Each isolated test file re-evaluates the full @spinnaker/core graph in its setup, so the
    // suite is dominated by per-file setup cost, not test execution. worker_threads have far lower
    // startup/teardown overhead than child-process forks and share the transform cache, which cuts
    // wall time materially (~4.3m -> ~3.3m) while keeping per-file isolation intact.
    pool: 'threads',
    setupFiles: [path.resolve(DECK_ROOT, 'vitest.setup.ts')],
    include: ['packages/*/src/**/*.spec.{js,ts,tsx}'],
    restoreMocks: true,
    // The per-file setup imports the full @spinnaker/core graph; under parallel load the default
    // 5s can be exceeded by legitimately-slow async specs. Give headroom without masking hangs.
    testTimeout: 20000,
    hookTimeout: 30000,
    server: {
      deps: {
        // Inline react-dom so named imports (e.g. `import { render } from 'react-dom'`) share
        // live bindings with the default import; specs that `vi.spyOn(ReactDOM, 'render')` then
        // intercept the same function the code under test calls (webpack CJS interop parity).
        inline: ['react-dom'],
      },
    },
    // Compile & apply CSS/LESS so specs asserting computed styles (stylesheet loading,
    // flex/display/color) behave as they did under Karma's real-browser style injection.
    css: true,
  },
});

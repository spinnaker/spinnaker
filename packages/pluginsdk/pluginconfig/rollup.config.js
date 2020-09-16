const { nodeResolve } = require('@rollup/plugin-node-resolve');
const commonjs = require('@rollup/plugin-commonjs');
const typescript = require('@rollup/plugin-typescript');
const postCss = require('rollup-plugin-postcss');
const externalGlobals = require('rollup-plugin-external-globals');

const ROLLUP_WATCH = !!process.env.ROLLUP_WATCH;

module.exports = {
  input: 'src/index.ts',
  plugins: [
    nodeResolve(),
    commonjs(),
    typescript({
      // In watch mode, always emit javascript even with errors (otherwise rollup will terminate)
      noEmitOnError: !ROLLUP_WATCH,
    }),
    // map imports from shared libraries (react, etc) to global variables exposed by spinnaker
    externalGlobals(spinnakerSharedLibraries()),
    // import from .css, .less, and inject into the document <head></head>
    postCss(),
  ],
  output: [{ dir: 'build/dist', format: 'es', sourcemap: true }],
};

function spinnakerSharedLibraries() {
  // Updates here should also be added in core/src/plugins/sharedLibraries.ts
  const libraries = [
    '@spinnaker/core',
    '@uirouter/core',
    '@uirouter/react',
    '@uirouter/rx',
    'lodash',
    'prop-types',
    'react',
    'react-dom',
    'rxjs',
    'rxjs/Observable',
  ];

  function getGlobalVariable(libraryName) {
    const prefix = 'spinnaker.plugins.sharedLibraries';
    const sanitizedLibraryName = libraryName.replace(/[^a-zA-Z0-9_]/g, '_');
    return `${prefix}.${sanitizedLibraryName}`;
  }

  return libraries.reduce((globalsMap, libraryName) => {
    return { ...globalsMap, [libraryName]: getGlobalVariable(libraryName) };
  }, {});
}

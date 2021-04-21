const { nodeResolve } = require('@rollup/plugin-node-resolve');

const commonjs = require('@rollup/plugin-commonjs');
const externalGlobals = require('rollup-plugin-external-globals');
const json = require('@rollup/plugin-json');
const postCss = require('rollup-plugin-postcss');
const replace = require('@rollup/plugin-replace');
const { terser } = require('rollup-plugin-terser');
const typescript = require('@rollup/plugin-typescript');
const url = require('@rollup/plugin-url');
const visualizer = require('rollup-plugin-visualizer');

const ROLLUP_STATS = !!process.env.ROLLUP_STATS;
const ROLLUP_WATCH = !!process.env.ROLLUP_WATCH;
const NODE_ENV = JSON.stringify(process.env.NODE_ENV || 'development');
const ENV_MINIFY = process.env.ROLLUP_MINIFY;
const ROLLUP_MINIFY = ENV_MINIFY === 'true' || (NODE_ENV === '"production"' && ENV_MINIFY !== 'false');

// eslint-disable-next-line no-console
console.log({ ROLLUP_STATS, ROLLUP_WATCH, ROLLUP_MINIFY, NODE_ENV });

const plugins = [
  nodeResolve(),
  commonjs(),
  json(),
  url({
    include: ['**/*.html', '**/*.svg', '**/*.png', '**/*.jp(e)?g', '**/*.gif', '**/*.webp'],
    fileName: '[dirname][hash][extname]',
  }),
  // Replace literal string 'process.env.NODE_ENV' with the current NODE_ENV
  replace({
    preventAssignment: true,
    values: { 'process.env.NODE_ENV': NODE_ENV },
  }),
  typescript({
    // In watch mode, always emit javascript even with errors (otherwise rollup will terminate)
    noEmitOnError: !ROLLUP_WATCH,
  }),
  // map imports from shared libraries (react, etc) to global variables exposed by spinnaker
  externalGlobals(spinnakerSharedLibraries()),
  // import from .css, .less, and inject into the document <head></head>
  postCss(),
];

if (ROLLUP_MINIFY) {
  plugins.push(
    terser({
      format: {
        comments: function (node, comment) {
          if (comment.type == 'comment2') {
            // Preserve multiline comments containing any of these strings
            return /@preserve|@license|@cc_on|webpackChunkName|webpackIgnore/i.test(comment.value);
          }
        },
      },
    }),
  );
}

if (ROLLUP_STATS) {
  plugins.push(visualizer({ sourcemap: true }));
}

module.exports = {
  input: 'src/index.ts',
  output: [{ dir: 'build/dist', format: 'es', sourcemap: true }],
  plugins,
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

  // This allows us to share libraries with plugin dependencies that would otherwise be double bundled
  // @rollup/plugin-commonjs rewrites library names in commonjs imported code
  // This block finds known permutations and replaces them with the shared global variable
  return libraries.reduce((globalsMap, libraryName) => {
    const globalVar = getGlobalVariable(libraryName);
    const libName2 = libraryName + '?commonjs-proxy';
    const libName3 = libraryName + '?commonjs-require';
    const libName4 = '\u0000' + libraryName + '?commonjs-proxy';
    const libName5 = '\u0000' + libraryName + '?commonjs-require';

    return {
      ...globalsMap,
      [libraryName]: globalVar,
      [libName2]: globalVar,
      [libName3]: globalVar,
      [libName4]: globalVar,
      [libName5]: globalVar,
    };
  }, {});
}

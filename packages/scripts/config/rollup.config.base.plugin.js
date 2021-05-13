const baseRollupConfig = require('./rollup.config.base');
const externalGlobals = require('rollup-plugin-external-globals');

module.exports = {
  ...baseRollupConfig,
  input: 'src/index.ts',
  output: [{ dir: 'build/dist', format: 'es', sourcemap: true }],
  plugins: [
    ...baseRollupConfig.plugins,
    // map imports from shared libraries (react, etc) to global variables exposed by spinnaker
    externalGlobals(spinnakerSharedLibraries()),
  ],
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

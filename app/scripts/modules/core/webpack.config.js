'use strict';
const { getBaseConfig } = require('../base.webpack.config');
const path = require('path');
const fs = require('fs');
const corePath = __dirname;
const basePath = path.join(__dirname, '..', '..', '..', '..');

const CopyWebpackPlugin = require('copy-webpack-plugin');

// In index.ts, the triple slash reference to global namespace types get re-written to match the 'core' alias
// This kludge re-re-writes them back to a relative path
// Likely related to https://github.com/microsoft/TypeScript/issues/36763
class KLUDGE_FixTypesReferencePlugin {
  apply(compiler) {
    compiler.hooks.afterEmit.tap('KLUDGE_FixTypesReferencePlugin', (file, rest) => {
      // eslint-disable-next-line no-console
      console.log('Fixing up core/index.d.ts...');
      const dts = path.join(corePath, 'lib', 'index.d.ts');
      const fixedup = fs
        .readFileSync(dts)
        .toString()
        .replace(/types="core\/types"/, 'types="./types"');
      fs.writeFileSync(dts, fixedup);
    });
  }
}

const baseConfig = getBaseConfig('core');

module.exports = {
  ...baseConfig,
  resolve: {
    ...baseConfig.resolve,
    alias: {
      ...baseConfig.resolve.alias,
      root: basePath,
    },
  },
  plugins: [
    ...baseConfig.plugins,
    new CopyWebpackPlugin([{ from: `${corePath}/src/types`, to: `types` }]),
    new KLUDGE_FixTypesReferencePlugin(),
  ],
  externals: [...baseConfig.externals, 'root/version.json'],
};

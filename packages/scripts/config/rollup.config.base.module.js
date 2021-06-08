const fs = require('fs');
const path = require('path');
const baseRollupConfig = require('./rollup.config.base');
const angularJsTemplateLoader = require('../helpers/rollup-plugin-angularjs-template-loader');
const externalConfigurer = require('../helpers/rollup-node-auto-external-configurer');
const stripCode = require('rollup-plugin-strip-code');

const packageJSON = JSON.parse(fs.readFileSync(path.resolve('package.json'), 'utf8'));

module.exports = {
  ...baseRollupConfig,
  input: 'src/index.ts',
  output: [{ dir: 'dist', format: 'es', sourcemap: true }],
  plugins: [
    ...baseRollupConfig.plugins,
    angularJsTemplateLoader({ sourceMap: true }),
    stripCode({
      start_comment: 'Start - Rollup Remove',
      end_comment: 'End - Rollup Remove',
    }),
  ],
  external: externalConfigurer(packageJSON.dependencies),
};

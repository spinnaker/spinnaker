const fs = require('fs');
const path = require('path');

const baseRollupConfig = require('./rollup.config.base');
const angularJsTemplateLoader = require('../helpers/rollup-plugin-angularjs-template-loader');
const externalConfigurer = require('../helpers/rollup-node-auto-external-configurer');

const packageJSON = JSON.parse(fs.readFileSync(path.resolve('package.json'), 'utf8'));

module.exports = {
  ...baseRollupConfig,
  input: 'src/index.ts',
  output: [{ dir: 'dist', format: 'es', sourcemap: true }],
  plugins: [angularJsTemplateLoader({ sourceMap: true }), ...baseRollupConfig.plugins],
  external: externalConfigurer(packageJSON.dependencies),
};

const baseRollupConfig = require('./rollup.config.base');
const angularJsTemplateLoader = require('../helpers/rollup-plugin-angularjs-template-loader');
const autoExternal = require('rollup-plugin-auto-external');

module.exports = {
  ...baseRollupConfig,
  input: 'src/index.ts',
  output: [{ dir: 'dist', format: 'es', sourcemap: true }],
  plugins: [...baseRollupConfig.plugins, angularJsTemplateLoader({ sourceMap: true }), autoExternal()],
  externals: ['rxjs/operators'],
};

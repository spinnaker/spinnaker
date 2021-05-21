const baseRollupConfig = require('./rollup.config.base');
const angularJsTemplateLoader = require('../helpers/rollup-plugin-angularjs-template-loader');
const autoExternal = require('rollup-plugin-auto-external');

module.exports = {
  ...baseRollupConfig,
  input: 'src/index.ts',
  output: [{ dir: 'dist', format: 'es', sourcemap: true }],
  plugins: [...baseRollupConfig.plugins, angularJsTemplateLoader({ sourceMap: true }), autoExternal()],
  // `autoExternal` cannot handle paths in import statements, so we need to explicitly add it in `externals`.
  externals: ['rxjs/operators'],
};

const baseRollupConfig = require('@spinnaker/scripts/config/rollup.config.base.module');

module.exports = {
  ...baseRollupConfig,
  external: ['@spinnaker/core'],
  input: 'src/index.ts',
  output: [{ dir: 'dist', format: 'es', sourcemap: true }],
};

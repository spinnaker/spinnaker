import alias from '@rollup/plugin-alias';

import baseRollupConfig from '@spinnaker/scripts/config/rollup.config.base.module';

export default {
  ...baseRollupConfig,
  plugins: [
    ...baseRollupConfig.plugins,
    alias({
      entries: [{ find: 'cloudfoundry', replacement: './src' }],
    }),
  ],
};

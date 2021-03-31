const basePluginConfig = require('@spinnaker/pluginsdk/pluginconfig/rollup.config');

export default {
  ...basePluginConfig,
  input: 'src/index.ts',
};

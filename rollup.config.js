const json = require('@rollup/plugin-json');
const visualizer = require('rollup-plugin-visualizer');
const basePluginConfig = require('@spinnaker/pluginsdk/pluginconfig/rollup.config');
basePluginConfig.plugins.push(json(), visualizer());

export default {
  ...basePluginConfig,
  input: 'src/index.ts',
};

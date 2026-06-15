// This rollup config uses the pluginsdk config as a base but this is not a plugin

const basePluginConfig = require('@spinnaker/pluginsdk/pluginconfig/rollup.config');
const typescript = require('@rollup/plugin-typescript');

basePluginConfig.plugins = basePluginConfig.plugins.filter(
  (x) => x.name !== 'rollup-plugin-external-globals' && x.name !== 'esbuild',
);
basePluginConfig.plugins.push(typescript());

const externals = [
  '@spinnaker/core',
  '@uirouter/react',
  '@uirouter/core',
  'formik',
  'lodash',
  'luxon',
  'prop-types',
  'react',
  'react-bootstrap',
  'react-select',
  'react-dom',
  'semiotic',
];

basePluginConfig.input = 'src/index.ts';
basePluginConfig.external = function (id) {
  return externals.includes(id);
};

export default basePluginConfig;

// This rollup config uses the pluginsdk config as a base but this is not a plugin

import angularJsTemplateLoader from './rollup-plugin-angularjs-template-loader';
const basePluginConfig = require('@spinnaker/pluginsdk/pluginconfig/rollup.config');

basePluginConfig.plugins = basePluginConfig.plugins.filter((x) => x.name !== 'rollup-plugin-external-globals');
basePluginConfig.plugins.push(angularJsTemplateLoader({ sourceMap: true }));

const externals = [
  'angular',
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

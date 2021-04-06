// This rollup config uses the pluginsdk config as a base but this is not a plugin

import angularJsTemplateLoader from './rollup-plugin-angularjs-template-loader';
const basePluginConfig = require('@spinnaker/pluginsdk/pluginconfig/rollup.config');

basePluginConfig.plugins = basePluginConfig.plugins.filter((x) => x.name !== 'rollup-plugin-external-globals');
basePluginConfig.plugins.push(angularJsTemplateLoader({ sourceMap: true }));

const external = [
  'angular',
  '@spinnaker/core',
  '@uirouter/react',
  '@uirouter/core',
  'lodash',
  'luxon',
  'prop-types',
  'formik',
  'react',
  'react-dom',
  'rxjs',
  'rxjs/Observable',
];

export default {
  ...basePluginConfig,
  input: 'src/index.ts',
  external,
};

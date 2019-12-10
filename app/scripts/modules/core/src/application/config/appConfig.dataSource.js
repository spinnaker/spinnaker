import { ApplicationDataSourceRegistry } from '../service/ApplicationDataSourceRegistry';
import { APP_CONFIG_STATES } from './appConfig.states';

const angular = require('angular');

export const CORE_APPLICATION_CONFIG_APPCONFIG_DATASOURCE = 'spinnaker.core.application.config.dataSource';
export const name = CORE_APPLICATION_CONFIG_APPCONFIG_DATASOURCE; // for backwards compatibility
angular.module(CORE_APPLICATION_CONFIG_APPCONFIG_DATASOURCE, [APP_CONFIG_STATES]).run(function() {
  ApplicationDataSourceRegistry.registerDataSource({
    key: 'config',
    label: 'Config',
    sref: '.config',
    active: '**.config.**',
    defaultData: [],
  });
});

import { ApplicationDataSourceRegistry } from '../service/ApplicationDataSourceRegistry';
import { APP_CONFIG_STATES } from './appConfig.states';

const angular = require('angular');

module.exports = angular.module('spinnaker.core.application.config.dataSource', [APP_CONFIG_STATES]).run(function() {
  ApplicationDataSourceRegistry.registerDataSource({
    key: 'config',
    label: 'Config',
    sref: '.config',
    active: '**.config.**',
    defaultData: [],
  });
});

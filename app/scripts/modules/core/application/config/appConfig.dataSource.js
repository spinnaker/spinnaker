import {DataSourceConfig} from '../service/applicationDataSource';
import {APPLICATION_DATA_SOURCE_REGISTRY} from '../service/applicationDataSource.registry';
import {APP_CONFIG_STATES} from './appConfig.states';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.application.config.dataSource', [
    APPLICATION_DATA_SOURCE_REGISTRY,
    APP_CONFIG_STATES,
  ])
  .run(function($q, applicationDataSourceRegistry) {
    applicationDataSourceRegistry.registerDataSource(new DataSourceConfig({
      key: 'config',
      label: 'Config',
      sref: '.config',
      active: '**.config.**',
    }));
  });

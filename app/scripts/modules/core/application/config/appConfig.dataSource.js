import {DataSourceConfig} from '../service/applicationDataSource';
import registryModule from '../service/applicationDataSource.registry';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.application.config.dataSource', [
    registryModule,
  ])
  .run(function($q, applicationDataSourceRegistry) {
    applicationDataSourceRegistry.registerDataSource(new DataSourceConfig({
      key: 'config',
      label: 'Config',
      sref: '.config',
      active: '**.config.**',
    }));
  });

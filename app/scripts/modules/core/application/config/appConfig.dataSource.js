import {DataSourceConfig} from '../service/applicationDataSource.ts';
import registryModule from '../service/applicationDataSource.registry.ts';

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

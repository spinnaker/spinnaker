import {DataSourceConfig} from '../service/applicationDataSource';
import {APPLICATION_DATA_SOURCE_REGISTRY} from '../service/applicationDataSource.registry';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.application.config.dataSource', [
    APPLICATION_DATA_SOURCE_REGISTRY,
  ])
  .run(function($q, applicationDataSourceRegistry) {
    applicationDataSourceRegistry.registerDataSource(new DataSourceConfig({
      key: 'config',
      label: 'Config',
      sref: '.config',
      active: '**.config.**',
    }));
  });

import {DataSourceConfig} from 'core/application/service/applicationDataSource';
import {APPLICATION_DATA_SOURCE_REGISTRY} from 'core/application/service/applicationDataSource.registry';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.netflix.tableau.dataSource', [
    require('core/config/settings'),
    APPLICATION_DATA_SOURCE_REGISTRY,
  ])
  .run(function($q, applicationDataSourceRegistry, settings) {
    if (settings.feature && settings.feature.tableau) {
      applicationDataSourceRegistry.registerDataSource(new DataSourceConfig({
        key: 'analytics',
        sref: '.analytics',
        optIn: true,
        optional: true,
        description: 'Pipeline and task statistics'
      }));
    }
  });

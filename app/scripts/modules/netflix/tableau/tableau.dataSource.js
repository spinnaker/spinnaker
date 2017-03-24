import {DataSourceConfig} from 'core/application/service/applicationDataSource';
import {APPLICATION_DATA_SOURCE_REGISTRY} from 'core/application/service/applicationDataSource.registry';
import {NetflixSettings} from '../netflix.settings';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.netflix.tableau.dataSource', [
    APPLICATION_DATA_SOURCE_REGISTRY
  ])
  .run(function($q, applicationDataSourceRegistry) {
    if (NetflixSettings.feature.tableau) {
      applicationDataSourceRegistry.registerDataSource(new DataSourceConfig({
        key: 'analytics',
        sref: '.analytics',
        optIn: true,
        optional: true,
        description: 'Pipeline and task statistics'
      }));
    }
  });

import {DataSourceConfig} from 'core/application/service/applicationDataSource';
import {APPLICATION_DATA_SOURCE_REGISTRY} from 'core/application/service/applicationDataSource.registry';
import {NetflixSettings} from '../netflix.settings';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.netflix.fastProperty.dataSource', [
    APPLICATION_DATA_SOURCE_REGISTRY
  ])
  .run(function($q, applicationDataSourceRegistry) {

    if (NetflixSettings.feature.fastProperty && NetflixSettings.feature.netflixMode) {
      applicationDataSourceRegistry.registerDataSource(new DataSourceConfig({
        key: 'properties',
        sref: '.propInsights.properties',
        optional: true,
        description: 'Fast property management'
      }));
    }

  });

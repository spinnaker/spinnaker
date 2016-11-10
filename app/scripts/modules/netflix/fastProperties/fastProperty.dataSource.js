import {DataSourceConfig} from 'core/application/service/applicationDataSource';
import {APPLICATION_DATA_SOURCE_REGISTRY} from 'core/application/service/applicationDataSource.registry';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.netflix.fastProperty.dataSource', [
    require('core/config/settings'),
    APPLICATION_DATA_SOURCE_REGISTRY,
  ])
  .run(function($q, applicationDataSourceRegistry, settings) {

    if (settings.feature && settings.feature.fastProperty && settings.feature.netflixMode) {
      applicationDataSourceRegistry.registerDataSource(new DataSourceConfig({
        key: 'properties',
        sref: '.propInsights.properties',
        optional: true,
        description: 'Fast property management'
      }));
    }

  });

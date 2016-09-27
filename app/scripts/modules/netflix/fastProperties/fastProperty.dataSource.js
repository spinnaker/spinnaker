import {DataSourceConfig} from '../../core/application/service/applicationDataSource.ts';
import dataSourceRegistryModule from '../../core/application/service/applicationDataSource.registry.ts';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.netflix.fastProperty.dataSource', [
    require('../../core/config/settings'),
    dataSourceRegistryModule
  ])
  .run(function($q, applicationDataSourceRegistry, settings) {

    if (settings.feature && settings.feature.fastProperty && settings.feature.netflixMode) {
      applicationDataSourceRegistry.registerDataSource(new DataSourceConfig({
        key: 'properties',
        sref: '.propInsights.properties',
        optional: true,
      }));
    }

  });

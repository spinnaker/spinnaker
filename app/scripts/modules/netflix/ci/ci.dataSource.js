import {DataSourceConfig} from '../../core/application/service/applicationDataSource.ts';
import dataSourceRegistryModule from '../../core/application/service/applicationDataSource.registry.ts';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.netflix.ci.dataSource', [
    require('../../core/config/settings'),
    dataSourceRegistryModule
  ])
  .run(function($q, applicationDataSourceRegistry, settings) {

    if (settings.feature && settings.feature.netflixMode) {
      applicationDataSourceRegistry.registerDataSource(new DataSourceConfig({
        key: 'ci',
        sref: '.ci',
        title: 'CI',
        label: 'CI',
        optional: true,
        optIn: true,
        description: 'Container-based continuous integration (alpha)',
      }));
    }

  });

import {DataSourceConfig} from 'core/application/service/applicationDataSource';
import dataSourceRegistryModule from 'core/application/service/applicationDataSource.registry';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.netflix.ci.dataSource', [
    require('core/config/settings'),
    dataSourceRegistryModule,
    require('./build.read.service'),
  ])
  .run(function($q, applicationDataSourceRegistry, settings, buildService) {

    if (settings.feature && settings.feature.netflixMode) {
      applicationDataSourceRegistry.registerDataSource(new DataSourceConfig({
        key: 'ci',
        sref: '.ci',
        badge: 'runningBuilds',
        title: 'CI',
        label: 'CI',
        optional: true,
        optIn: true,
        description: 'Container-based continuous integration (alpha)',
      }));

      let loadRunningBuilds = (application) => {
        let attr = application.attributes;
        return buildService.getRunningBuilds(attr.repoType, attr.repoProjectKey, attr.repoSlug);
      };

      let buildsLoaded = (application, data) => {
        return $q.when(data);
      };

      applicationDataSourceRegistry.registerDataSource(new DataSourceConfig({
        key: 'runningBuilds',
        visible: false,
        description: 'Container-based continuous integration (alpha)',
        loader: loadRunningBuilds,
        onLoad: buildsLoaded,
      }));
    }

  });

import {DataSourceConfig} from 'core/application/service/applicationDataSource';
import {APPLICATION_DATA_SOURCE_REGISTRY} from 'core/application/service/applicationDataSource.registry';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.netflix.ci.dataSource', [
    require('core/config/settings'),
    APPLICATION_DATA_SOURCE_REGISTRY,
    require('./build.read.service'),
  ])
  .run(function($q, applicationDataSourceRegistry, settings, buildService) {

    if (settings.feature && settings.feature.netflixMode) {
      let loadRunningBuilds = (application) => {
        let attr = application.attributes;
        return buildService.getRunningBuilds(attr.repoType, attr.repoProjectKey, attr.repoSlug);
      };

      let buildsLoaded = (application, data) => {
        return $q.when(data);
      };

      let loadBuilds = (application) => {
        const attr = application.attributes;
        if ([attr.repoType, attr.repoProjectKey, attr.repoSlug].every((attr) => _.trim(attr))) {
          return buildService.getBuilds(attr.repoType, attr.repoProjectKey, attr.repoSlug);
        }
        return $q.when([]);
      };

      applicationDataSourceRegistry.registerDataSource(new DataSourceConfig({
        key: 'ci',
        sref: '.ci',
        badge: 'runningBuilds',
        title: 'CI',
        label: 'CI',
        optional: true,
        optIn: true,
        lazy: true,
        description: 'Container-based continuous integration (alpha)',
        loader: loadBuilds,
        onLoad: buildsLoaded,
      }));

      applicationDataSourceRegistry.registerDataSource(new DataSourceConfig({
        key: 'runningBuilds',
        visible: false,
        description: 'Container-based continuous integration (alpha)',
        loader: loadRunningBuilds,
        onLoad: buildsLoaded,
      }));
    }

  });

import { APPLICATION_DATA_SOURCE_REGISTRY, DataSourceConfig, EXECUTION_SERVICE } from '@spinnaker/core';

import { NetflixSettings } from '../netflix.settings';
import { FAST_PROPERTY_READ_SERVICE } from './fastProperty.read.service';

module.exports = angular.module('spinnaker.netflix.fastProperty.dataSource', [
    APPLICATION_DATA_SOURCE_REGISTRY,
    EXECUTION_SERVICE,
    FAST_PROPERTY_READ_SERVICE,
  ])
  .run(function($q, applicationDataSourceRegistry, executionService, fastPropertyReader) {

    const loadRunningPromotions = (application) => {
      return fastPropertyReader.getPromotionsForApplication(application.name, executionService.activeStatuses);
    };

    const addRunningPromotions = (application, data) => $q.when(data);

    const loadPromotions = (application) => fastPropertyReader.getPromotionsForApplication(application.name);

    const addPromotions = (application, data) => $q.when(data);

    const loadApplicationProperties = (application) => fastPropertyReader.fetchForAppName(application.name);

    const addApplicationProperties = (application, data) => $q.when(fastPropertyReader.addFastPropertiesToApplication(application, data));

    if (NetflixSettings.feature.fastProperty && NetflixSettings.feature.netflixMode) {

      applicationDataSourceRegistry.registerDataSource(new DataSourceConfig({
        key: 'properties',
        sref: '.propInsights.properties',
        optional: true,
        lazy: true,
        loader: loadApplicationProperties,
        onLoad: addApplicationProperties,
        badge: 'runningPropertyPromotions',
        description: 'Fast property management'
      }));

      applicationDataSourceRegistry.registerDataSource(new DataSourceConfig({
        key: 'runningPropertyPromotions',
        visible: false,
        loader: loadRunningPromotions,
        onLoad: addRunningPromotions,
      }));

      applicationDataSourceRegistry.registerDataSource(new DataSourceConfig({
        key: 'propertyPromotions',
        loader: loadPromotions,
        onLoad: addPromotions,
        lazy: true,
        visible: false,
      }));
    }

  });

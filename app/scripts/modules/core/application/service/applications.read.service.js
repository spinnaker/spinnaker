import dataSourceModule from './applicationDataSource.registry';
import {ApplicationDataSource, DataSourceConfig} from '../service/applicationDataSource';
import {Application} from '../application.model';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.applications.read.service', [
    require('../../scheduler/scheduler.factory.js'),
    require('../../api/api.service'),
    dataSourceModule,
    require('../../presentation/robotToHumanFilter/robotToHuman.filter'),
  ])
  .factory('applicationReader', function ($q, $log, $filter, $http, settings, API, schedulerFactory,
                                          applicationDataSourceRegistry) {

    function listApplications() {
      return API.one('applications').useCache().get();
    }

    function getApplication(applicationName) {
      return $http.get([settings.gateUrl, 'applications', applicationName].join('/'))
        .then((response) => {
          let application = new Application(applicationName, schedulerFactory.createScheduler(), $q, $log);
          application.attributes = response.data.attributes;
          addDataSources(application);
          application.refresh();
          return application;
        });
    }

    function addDataSources(application) {
      let dataSources = applicationDataSourceRegistry.getDataSources();
      dataSources.forEach((ds) => {
        let dataSource = new ApplicationDataSource(new DataSourceConfig(ds), application, $q, $log, $filter);
        application.dataSources.push(dataSource);
        application[ds.key] = dataSource;
      });
      setDisabledDataSources(application);
    }

    function setDisabledDataSources(application) {
      let allDataSources = application.dataSources;
      if (!application.attributes.dataSources) {
        allDataSources.filter(ds => ds.optIn).forEach(ds => ds.disabled = true);
      } else {
        let appDataSources = application.attributes.dataSources;
        allDataSources.forEach(ds => {
          if (ds.optional) {
            if (ds.optIn && !appDataSources.enabled.includes(ds.key)) {
              ds.disabled = true;
            }
            if (!ds.optIn && ds.optional && appDataSources.disabled.includes(ds.key)) {
              ds.disabled = true;
            }
          }
        });
      }
    }

    return {
      listApplications: listApplications,
      getApplication: getApplication
    };
  });

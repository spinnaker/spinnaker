import {DataSourceConfig} from '../application/service/applicationDataSource';
import {APPLICATION_DATA_SOURCE_REGISTRY} from '../application/service/applicationDataSource.registry';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.task.dataSource', [
    APPLICATION_DATA_SOURCE_REGISTRY,
    require('./task.read.service'),
    require('../cluster/cluster.service'),
  ])
  .run(function($q, applicationDataSourceRegistry, taskReader, clusterService) {

    let addTasks = (application, tasks) => {
      return $q.when(angular.isArray(tasks) ? tasks : []);
    };

    let loadTasks = (application) => {
      return taskReader.getTasks(application.name);
    };

    let loadRunningTasks = (application) => {
      return taskReader.getRunningTasks(application.name);
    };

    let addRunningTasks = (application, data) => {
      clusterService.addTasksToServerGroups(application);
      return $q.when(data);
    };

    applicationDataSourceRegistry.registerDataSource(new DataSourceConfig({
      key: 'tasks',
      sref: '.tasks',
      badge: 'runningTasks',
      loader: loadTasks,
      onLoad: addTasks,
      lazy: true,
    }));

    applicationDataSourceRegistry.registerDataSource(new DataSourceConfig({
      key: 'runningTasks',
      visible: false,
      loader: loadRunningTasks,
      onLoad: addRunningTasks,
    }));
  });

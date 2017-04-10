import {DataSourceConfig} from '../application/service/applicationDataSource';
import {APPLICATION_DATA_SOURCE_REGISTRY} from '../application/service/applicationDataSource.registry';
import {TASK_READ_SERVICE} from 'core/task/task.read.service';
import {CLUSTER_SERVICE} from 'core/cluster/cluster.service';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.task.dataSource', [
    APPLICATION_DATA_SOURCE_REGISTRY,
    TASK_READ_SERVICE,
    CLUSTER_SERVICE,
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
      return $q.when(data);
    };

    let runningTasksLoaded = (application) => {
      clusterService.addTasksToServerGroups(application);
      application.getDataSource('serverGroups').dataUpdated();
    };

    applicationDataSourceRegistry.registerDataSource(new DataSourceConfig({
      key: 'tasks',
      sref: '.tasks',
      badge: 'runningTasks',
      loader: loadTasks,
      onLoad: addTasks,
      afterLoad: runningTasksLoaded,
      lazy: true,
      primary: true,
      icon: 'list',
    }));

    applicationDataSourceRegistry.registerDataSource(new DataSourceConfig({
      key: 'runningTasks',
      visible: false,
      loader: loadRunningTasks,
      onLoad: addRunningTasks,
    }));
  });

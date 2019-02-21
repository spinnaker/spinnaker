import { ApplicationDataSourceRegistry } from '../application/service/ApplicationDataSourceRegistry';
import { TaskReader } from 'core/task/task.read.service';
import { CLUSTER_SERVICE } from 'core/cluster/cluster.service';

const angular = require('angular');

module.exports = angular.module('spinnaker.core.task.dataSource', [CLUSTER_SERVICE]).run(['$q', 'clusterService', function($q, clusterService) {
  let addTasks = (application, tasks) => {
    return $q.when(angular.isArray(tasks) ? tasks : []);
  };

  let loadTasks = application => {
    return TaskReader.getTasks(application.name);
  };

  let loadRunningTasks = application => {
    return TaskReader.getRunningTasks(application.name);
  };

  let addRunningTasks = (application, data) => {
    return $q.when(data);
  };

  let runningTasksLoaded = application => {
    clusterService.addTasksToServerGroups(application);
    application.getDataSource('serverGroups').dataUpdated();
  };

  ApplicationDataSourceRegistry.registerDataSource({
    key: 'tasks',
    sref: '.tasks',
    badge: 'runningTasks',
    category: 'tasks',
    loader: loadTasks,
    onLoad: addTasks,
    afterLoad: runningTasksLoaded,
    lazy: true,
    primary: true,
    icon: 'fa fa-sm fa-fw fa-check-square',
  });

  ApplicationDataSourceRegistry.registerDataSource({
    key: 'runningTasks',
    visible: false,
    loader: loadRunningTasks,
    onLoad: addRunningTasks,
  });
}]);

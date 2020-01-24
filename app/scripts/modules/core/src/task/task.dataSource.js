import { ApplicationDataSourceRegistry } from '../application/service/ApplicationDataSourceRegistry';
import { TaskReader } from './task.read.service';
import { CLUSTER_SERVICE } from 'core/cluster/cluster.service';

import * as angular from 'angular';

export const CORE_TASK_TASK_DATASOURCE = 'spinnaker.core.task.dataSource';
export const name = CORE_TASK_TASK_DATASOURCE; // for backwards compatibility
angular.module(CORE_TASK_TASK_DATASOURCE, [CLUSTER_SERVICE]).run([
  '$q',
  'clusterService',
  function($q, clusterService) {
    const addTasks = (application, tasks) => {
      return $q.when(angular.isArray(tasks) ? tasks : []);
    };

    const loadTasks = application => {
      return TaskReader.getTasks(application.name);
    };

    const loadRunningTasks = application => {
      return TaskReader.getRunningTasks(application.name);
    };

    const addRunningTasks = (application, data) => {
      return $q.when(data);
    };

    const runningTasksLoaded = application => {
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
      defaultData: [],
    });

    ApplicationDataSourceRegistry.registerDataSource({
      key: 'runningTasks',
      visible: false,
      loader: loadRunningTasks,
      onLoad: addRunningTasks,
      defaultData: [],
    });
  },
]);

import * as angular from 'angular';

import { ApplicationDataSourceRegistry } from '../application/service/ApplicationDataSourceRegistry';
import { CLUSTER_SERVICE } from '../cluster/cluster.service';
import { SETTINGS } from '../config';
import { TaskReader } from './task.read.service';

export const CORE_TASK_TASK_DATASOURCE = 'spinnaker.core.task.dataSource';
export const name = CORE_TASK_TASK_DATASOURCE; // for backwards compatibility
angular.module(CORE_TASK_TASK_DATASOURCE, [CLUSTER_SERVICE]).run([
  '$q',
  'clusterService',
  function ($q, clusterService) {
    const addTasks = (application, tasks) => {
      return $q.when(angular.isArray(tasks) ? tasks : []);
    };

    const loadPaginatedTasks = async (application, page = 1) => {
      let limitPerPage = SETTINGS.tasksViewLimitPerPage;
      const tasks = await TaskReader.getTasks(application.name, [], limitPerPage, page);
      if (tasks.length === limitPerPage) {
        return tasks.concat(await loadPaginatedTasks(application, page + 1));
      } else {
        return tasks;
      }
    };

    const loadTasks = (application, page = 1) => {
      let limitPerPage = SETTINGS.tasksViewLimitPerPage;
      if (limitPerPage === undefined) {
        return TaskReader.getTasks(application.name);
      } else {
        return loadPaginatedTasks(application, page);
      }
    };

    const loadRunningTasks = (application) => {
      return TaskReader.getRunningTasks(application.name);
    };

    const addRunningTasks = (application, data) => {
      return $q.when(data);
    };

    const runningTasksLoaded = (application) => {
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
      iconName: 'spMenuTasks',
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

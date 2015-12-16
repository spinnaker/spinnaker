'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.task.read.service', [
    require('./tasks.api.js'),
  ])
  .factory('taskReader', function(tasksApi) {

    const activeStatuses = ['RUNNING', 'SUSPENDED', 'NOT_STARTED'];

    function getRunningTasks(applicationName) {
      return getTasks(applicationName, activeStatuses);
    }

    function getTasks(applicationName, statuses=[]) {
      return tasksApi.one('applications', applicationName).all('tasks').getList({statuses: statuses.join(',')})
        .then(function(tasks) {
          return tasks.filter(function(task) {
            return !task.getValueFor('dryRun');
          });
        });
    }


    function getOneTaskForApplication(applicationName, taskId) {
      return tasksApi.one('applications', applicationName).one('tasks', taskId).get();
    }

    return {
      getTasks: getTasks,
      getRunningTasks: getRunningTasks,
      getOneTaskForApplication: getOneTaskForApplication

    };

  });

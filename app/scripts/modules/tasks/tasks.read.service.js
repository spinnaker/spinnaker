'use strict';

angular
  .module('spinnaker.tasks.read.service', ['spinnaker.tasks.api'])
  .factory('tasksReader', function(tasksApi) {

    function listAllTasksForApplication(applicationName) {
      return tasksApi.one('applications', applicationName).all('tasks').getList();
    }


    function getOneTaskForApplication(applicationName, taskId) {
      return tasksApi.one('applications', applicationName).one('tasks', taskId).get();
    }

    return {
      listAllTasksForApplication: listAllTasksForApplication,
      getOneTaskForApplication: getOneTaskForApplication

    };

  });

'use strict';

angular
  .module('deckApp.tasks.read.service', ['deckApp.tasks.api'])
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

'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.tasks.read.service', [
    require('./tasks.api.config.js'),
  ])
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

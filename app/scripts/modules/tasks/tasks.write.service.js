'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.tasks.write.service', [
    require('restangular'),
  ])
  .factory('tasksWriter', function(Restangular) {

    var endpoint = Restangular.all('applications');

    function getEndpoint(application) {
      return endpoint.all(application).all('tasks');
    }

    function postTaskCommand(taskCommand) {
      return getEndpoint(taskCommand.application).post(taskCommand);
    }

    function cancelTask(application, taskId) {
      return getEndpoint(application).one(taskId).one('cancel').put();
    }

    function deleteTask(taskId) {
      return Restangular.all('tasks').one(taskId).remove();
    }

    return {
      postTaskCommand: postTaskCommand,
      cancelTask: cancelTask,
      deleteTask: deleteTask,
    };

  });

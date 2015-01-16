'use strict';

angular
  .module('deckApp.tasks.write.service', ['restangular'])
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

    return {
      postTaskCommand: postTaskCommand,
      cancelTask: cancelTask,
    };

  });

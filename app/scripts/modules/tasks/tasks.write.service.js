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

    return {
      postTaskCommand: postTaskCommand
    };

  });

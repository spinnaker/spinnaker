'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.taskExecutor', [
  require('exports?"restangular"!imports?_=lodash!restangular'),
  require('../authentication/authentication.service.js'),
  require('./task.read.service.js'),
  require('./task.write.service.js'),
])
  .factory('taskExecutor', function(Restangular, $q, authenticationService, taskReader, taskWriter) {


    function executeTask(taskCommand) {
      let owner = taskCommand.application || taskCommand.project || { name: 'ad-hoc'};
      if (taskCommand.application) {
        taskCommand.application = taskCommand.application.name;
      }
      if (taskCommand.project) {
        taskCommand.project = taskCommand.project.name;
      }

      if (taskCommand.job[0].providerType === 'aws') {
        delete taskCommand.job[0].providerType;
      }

      taskCommand.job.forEach(function(job) {
        job.user = authenticationService.getAuthenticatedUser().name;
      });

      return taskWriter.postTaskCommand(taskCommand).then(
        function(task) {
          var taskId = task.ref.substring(task.ref.lastIndexOf('/') + 1);

          if (owner.reloadTasks) {
            owner.reloadTasks();
          }
          return taskReader.getTask(owner.name, taskId);
        },
        function(response) {
          var error = {
            status: response.status,
            message: response.statusText
          };
          if (response.data && response.data.message) {
            error.log = response.data.message;
          } else {
            error.log = 'Sorry, no more information.';
          }
          return $q.reject(error);
        }
      );
    }


    return {
      executeTask: executeTask,
    };
  });

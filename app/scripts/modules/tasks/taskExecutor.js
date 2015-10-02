'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.taskExecutor.service', [
  require('exports?"restangular"!imports?_=lodash!restangular'),
  require('../core/cache/deckCacheFactory.js'),
  require('../scheduler/scheduler.service.js'),
  require('../core/authentication/authentication.service.js'),
  require('../core/cache/infrastructureCaches.js'),
  require('./tasks.read.service.js'),
  require('./tasks.write.service.js'),
])
  .factory('taskExecutor', function(Restangular, scheduler, $q, authenticationService, tasksReader, tasksWriter) {


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

      var op = tasksWriter.postTaskCommand(taskCommand).then(
        function(task) {
          var taskId = task.ref.substring(task.ref.lastIndexOf('/')+1);

          if (owner.reloadTasks) {
            owner.reloadTasks();
          }
          return tasksReader.getOneTaskForApplication(owner.name, taskId);
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
      return scheduler.scheduleOnCompletion(op);
    }


    return {
      executeTask: executeTask,
    };
  }).name;

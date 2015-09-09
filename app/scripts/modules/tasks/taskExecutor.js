'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.taskExecutor.service', [
  require('exports?"restangular"!imports?_=lodash!restangular'),
  require('../caches/deckCacheFactory.js'),
  require('../scheduler/scheduler.service.js'),
  require('../authentication/authentication.module.js'),
  require('../authentication/authenticationService.js'),
  require('../caches/scheduledCache.js'),
  require('../caches/infrastructureCaches.js'),
  require('./tasks.read.service.js'),
  require('./tasks.write.service.js'),
])
  .factory('taskExecutor', function(Restangular, scheduler, $q, authenticationService, tasksReader, tasksWriter) {


    function executeTask(taskCommand) {
      var application = taskCommand.application;
      taskCommand.application = application.name;

      taskCommand.job.forEach(function(job) {
        job.user = authenticationService.getAuthenticatedUser().name;
      });

      var op = tasksWriter.postTaskCommand(taskCommand).then(
        function(task) {
          var taskId = task.ref.substring(task.ref.lastIndexOf('/')+1);

          if (application.reloadTasks) {
            application.reloadTasks();
          }
          return tasksReader.getOneTaskForApplication(application.name, taskId);
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

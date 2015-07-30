'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.taskExecutor.service', [
  require('exports?"restangular"!imports?_=lodash!restangular'),
  require('../modules/caches/deckCacheFactory.js'),
  require('../modules/scheduler/scheduler.service.js'),
  require('./urlbuilder.js'),
  require('../modules/authentication/authentication.module.js'),
  require('../modules/authentication/authenticationService.js'),
  require('../modules/caches/scheduledCache.js'),
  require('../modules/caches/infrastructureCaches.js'),
  require('../modules/tasks/tasks.read.service.js'),
  require('../modules/tasks/tasks.write.service.js'),
])
  .factory('taskExecutor', function(Restangular, scheduler, urlBuilder, $q, authenticationService, tasksReader, tasksWriter) {


    function executeTask(taskCommand) {
      var application = taskCommand.application;
      taskCommand.application = application.name;

      if (taskCommand.job[0].providerType === 'aws') {
        delete taskCommand.job[0].providerType;
      }

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

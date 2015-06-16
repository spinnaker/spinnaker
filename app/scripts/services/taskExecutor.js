'use strict';


angular.module('spinnaker.taskExecutor.service', [
  'restangular',
  'spinnaker.settings',
  'spinnaker.scheduler',
  'spinnaker.urlBuilder',
  'spinnaker.authentication',
  'spinnaker.authentication.service',
  'spinnaker.caches.scheduled',
  'spinnaker.caches.infrastructure',
  'spinnaker.tasks.read.service',
  'spinnaker.tasks.write.service',
])
  .factory('taskExecutor', function(settings, Restangular, scheduler, urlBuilder, $q, authenticationService, scheduledCache, infrastructureCaches, tasksReader, tasksWriter) {


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
  });

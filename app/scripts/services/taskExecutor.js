'use strict';


angular.module('deckApp')
  .factory('taskExecutor', function(settings, Restangular, scheduler, notificationsService, urlBuilder, $q, authenticationService, scheduledCache, infrastructureCaches, tasksReader, tasksWriter) {


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

          if(!taskCommand.supressNotification) {
            notificationsService.create({
              title: application.name,
              message: taskCommand.description,
              href: urlBuilder.buildFromMetadata({
                type: 'task',
                application: application.name,
                taskId: taskId
              })
            });
          }
          application.reloadTasks();
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

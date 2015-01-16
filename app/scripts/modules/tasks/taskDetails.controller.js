'use strict';


angular.module('deckApp.tasks.detail', [])
  .controller('TaskDetailsCtrl', function($scope, $log, taskId, application, $state, notificationsService, tasksWriter) {

    var vm = this;

    function extractTaskDetails() {
      var filtered = application.tasks.filter(function(task) {
        return task.id === taskId;
      });
      if (!filtered.length) {
        notificationsService.create({
          message: 'No task with id "' + taskId + '" was found.',
          autoDismiss: true,
          hideTimestamp: true,
          strong: true
        });
        $state.go('^');
      } else {
        vm.task = filtered[0];
      }
    }

    extractTaskDetails();

    application.registerAutoRefreshHandler(extractTaskDetails, $scope);

    vm.retry = angular.noop;
    vm.cancel = function() {
      tasksWriter.cancelTask(application.name, taskId);
    };

    return vm;
  });

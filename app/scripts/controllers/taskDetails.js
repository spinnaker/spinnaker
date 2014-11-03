'use strict';


angular.module('deckApp')
  .controller('TaskDetailsCtrl', function($scope, taskId, application, $state, notifications) {

    function extractTaskDetails() {
      var filtered = application.tasks.filter(function(task) {
        return task.id === taskId;
      });
      if (!filtered.length) {
        notifications.create({
          message: 'No task with id "' + taskId + '" was found.',
          autoDismiss: true,
          hideTimestamp: true,
          strong: true
        });
        $state.go('^');
      } else {
        $scope.task = filtered[0];
      }
    }

    extractTaskDetails();

    application.registerAutoRefreshHandler(extractTaskDetails, $scope);
  });

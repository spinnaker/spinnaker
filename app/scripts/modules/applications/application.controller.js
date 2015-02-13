'use strict';


angular.module('deckApp.application.controller', [
  'deckApp.delivery.executions.service',
  'deckApp.tasks.tracker'
])
  .controller('ApplicationCtrl', function($scope, application, executionsService, taskTracker) {
    $scope.application = application;
    $scope.insightTarget = application;
    if (application.notFound) {
      return;
    }

    application.enableAutoRefresh($scope);

    executionsService.getAll().then(function(oldExecutions) {
      $scope.executions = oldExecutions;
      var subscription = executionsService.subscribeAll(function(newExecutions) {
        taskTracker.handleTaskUpdates(oldExecutions, newExecutions);
        $scope.executions = newExecutions;
        oldExecutions = newExecutions;
      });
      $scope.$on('$destroy', function() {
        subscription.dispose();
      });
    });

  }
);


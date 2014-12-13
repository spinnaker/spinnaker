'use strict';


angular.module('deckApp')
  .controller('ApplicationCtrl', function($scope, application, executionsService, taskTracker) {
    $scope.application = application;
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


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

    function countInstances() {
      var serverGroups = application.serverGroups || [];
      return serverGroups
        .reduce(function(total, serverGroup) {
          return serverGroup.instances.length + total;
        }, 0);
    }

    if (countInstances() < 500) {
      application.enableAutoRefresh($scope);
    }

    executionsService.getAll(application.name).then(function(oldExecutions) {
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


'use strict';


angular.module('deckApp')
  .controller('ApplicationCtrl', function($scope, application, pipelines, taskTracker) {
    $scope.application = application;
    application.enableAutoRefresh($scope);

    pipelines.getAll().then(function(oldExecutions) {
      $scope.pipelines = oldExecutions;
      var subscription = pipelines.subscribeAll(function(newExecutions) {
        taskTracker.handleTaskUpdates(oldExecutions, newExecutions);
        $scope.pipelines = newExecutions;
        oldExecutions = newExecutions;
      });
      $scope.$on('$destroy', function() {
        subscription.dispose();
      });
    });

  }
);


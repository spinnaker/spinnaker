'use strict';

angular.module('spinnaker.serverGroup.display.tasks.tag', [
  'spinnaker.executionFilter.service'
])
  .directive('runningTasksTag', function() {
    return {
      restrict: 'E',
      scope: {
        application: '=',
        tasks: '=',
        executions: '='
      },
      templateUrl: 'scripts/modules/serverGroups/pod/runningTasksTag.html',
      controller: 'RunningTaskTagController',
    };
  })
  .controller('RunningTaskTagController', function ($scope, executionFilterService) {
    $scope.popover = { show : false };
    $scope.runningExecutions = function() {
      return executionFilterService.filterRunningExecutions($scope.executions);
    };

  });

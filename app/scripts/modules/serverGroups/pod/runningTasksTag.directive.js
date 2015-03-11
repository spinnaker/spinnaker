'use strict';

angular.module('deckApp.serverGroup.display.tasks.tag', [])
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
  .controller('RunningTaskTagController', function ($scope) {
    $scope.popover = { show : false };
    $scope.runningExecutions = function() {
      return _.filter($scope.executions, function(exe){
        return exe.isRunning || exe.hasNotStarted;
      });
    };
  });

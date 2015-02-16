'use strict';


angular.module('deckApp.taskView.directive', [])
  .directive('taskView', function () {
    return {
      restrict: 'E',
      templateUrl: 'scripts/modules/tasks/taskview.html',
      scope: {
        task: '=',
      },
      controller: function ($scope) {
        $scope.open = false;
        $scope.toggleDetails = function () {
          $scope.open = !$scope.open;
        };

      },
    };
  }
);

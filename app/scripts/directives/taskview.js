'use strict';


angular.module('deckApp')
  .directive('taskView', function () {
    return {
      restrict: 'E',
      templateUrl: 'views/task.html',
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

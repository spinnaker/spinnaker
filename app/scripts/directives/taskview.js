'use strict';

module.exports = function() {
  return {
    restrict: 'E',
    templateUrl: 'views/task.html',
    scope: {
      task: '=',
    },
    controller: function($scope) {
      $scope.open = false;
      $scope.toggleDetails = function() {
        $scope.open = !$scope.open;
      };

    },
  };

};

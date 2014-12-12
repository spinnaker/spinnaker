'use strict';

angular.module('deckApp')
  .directive('serverGroupCapacitySelector', function() {
    return {
      restrict: 'E',
      scope: {
        command: '=',
      },
      templateUrl: 'views/application/modal/serverGroup/aws/serverGroupCapacityDirective.html',
      controller: 'ServerGroupCapacitySelectorCtrl as serverGroupCapacityCtrl',
    }
  })
  .controller('ServerGroupCapacitySelectorCtrl', function($scope) {
    $scope.$watch('command.capacity.desired', function(newVal) {
      if ($scope.command.viewState.useSimpleCapacity) {
        $scope.command.capacity.min = newVal;
        $scope.command.capacity.max = newVal;
      }
    });

  });

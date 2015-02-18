'use strict';

angular.module('deckApp.serverGroup.configure.aws')
  .directive('awsServerGroupCapacitySelector', function() {
    return {
      restrict: 'E',
      scope: {
        command: '=',
      },
      templateUrl: 'scripts/modules/serverGroups/configure/aws/serverGroupCapacityDirective.html',
      controller: 'ServerGroupCapacitySelectorCtrl as serverGroupCapacityCtrl',
    };
  })
  .controller('ServerGroupCapacitySelectorCtrl', function($scope) {
    $scope.$watch('command.capacity.desired', function(newVal) {
      if ($scope.command.viewState.useSimpleCapacity) {
        $scope.command.capacity.min = newVal;
        $scope.command.capacity.max = newVal;
      }
    });

  });

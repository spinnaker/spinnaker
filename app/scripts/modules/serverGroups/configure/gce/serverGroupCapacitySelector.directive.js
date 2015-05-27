'use strict';

angular.module('spinnaker.serverGroup.configure.gce')
  .directive('gceServerGroupCapacitySelector', function() {
    return {
      restrict: 'E',
      scope: {
        command: '=',
      },
      templateUrl: 'scripts/modules/serverGroups/configure/gce/serverGroupCapacityDirective.html',
      controller: 'gceServerGroupCapacitySelectorCtrl as serverGroupCapacityCtrl',
    };
  })
  .controller('gceServerGroupCapacitySelectorCtrl', function($scope) {
    $scope.setMinMax = function(newVal) {
      $scope.command.capacity.min = newVal;
      $scope.command.capacity.max = newVal;
    };
  });

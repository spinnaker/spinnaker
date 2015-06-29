'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.aws')
  .directive('awsServerGroupCapacitySelector', function() {
    return {
      restrict: 'E',
      scope: {
        command: '=',
      },
      templateUrl: require('./serverGroupCapacityDirective.html'),
      controller: 'ServerGroupCapacitySelectorCtrl as serverGroupCapacityCtrl',
    };
  })
  .controller('ServerGroupCapacitySelectorCtrl', function($scope) {
    $scope.setSimpleCapacity = function(simpleCapacity) {
      $scope.command.viewState.useSimpleCapacity = simpleCapacity;
      $scope.command.useSourceCapacity = false;
      $scope.setMinMax($scope.command.capacity.desired);
    };
    $scope.setMinMax = function(newVal) {
      if ($scope.command.viewState.useSimpleCapacity) {
        $scope.command.capacity.min = newVal;
        $scope.command.capacity.max = newVal;
        $scope.command.useSourceCapacity = false;
      }
    };
  });

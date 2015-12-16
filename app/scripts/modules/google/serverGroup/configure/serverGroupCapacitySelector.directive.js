'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.google.serverGroup.capacity.selector.directive', [])
  .directive('gceServerGroupCapacitySelector', function() {
    return {
      restrict: 'E',
      scope: {
        command: '=',
      },
      templateUrl: require('./serverGroupCapacityDirective.html'),
      controller: 'gceServerGroupCapacitySelectorCtrl as serverGroupCapacityCtrl',
    };
  })
  .controller('gceServerGroupCapacitySelectorCtrl', function($scope) {
    $scope.setMinMax = function(newVal) {
      $scope.command.capacity.min = newVal;
      $scope.command.capacity.max = newVal;
    };
  });

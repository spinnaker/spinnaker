'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.titan.serverGroup.capacity.selector.directive', [])
  .directive('titanServerGroupCapacitySelector', function() {
    return {
      restrict: 'E',
      scope: {
        command: '=',
      },
      templateUrl: require('./serverGroupCapacityDirective.html'),
      controller: 'titanServerGroupCapacitySelectorCtrl as serverGroupCapacityCtrl',
    };
  })
  .controller('titanServerGroupCapacitySelectorCtrl', function($scope) {
    $scope.setMinMax = function(newVal) {
      $scope.command.capacity.min = newVal;
      $scope.command.capacity.max = newVal;
    };
  });

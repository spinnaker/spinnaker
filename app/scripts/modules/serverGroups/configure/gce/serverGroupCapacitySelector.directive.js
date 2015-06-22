'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.gce')
  .directive('gceServerGroupCapacitySelector', function() {
    return {
      restrict: 'E',
      scope: {
        command: '=',
      },
      template: require('./serverGroupCapacityDirective.html'),
      controller: 'gceServerGroupCapacitySelectorCtrl as serverGroupCapacityCtrl',
    };
  })
  .controller('gceServerGroupCapacitySelectorCtrl', function($scope) {
    $scope.setMinMax = function(newVal) {
      $scope.command.capacity.min = newVal;
      $scope.command.capacity.max = newVal;
    };
  });

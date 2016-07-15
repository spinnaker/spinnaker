'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.titus.serverGroup.capacity.selector.directive', [])
  .directive('titusServerGroupCapacitySelector', function() {
    return {
      restrict: 'E',
      scope: {
        command: '=',
      },
      templateUrl: require('./serverGroupCapacityDirective.html'),
      controller: 'titusServerGroupCapacitySelectorCtrl as serverGroupCapacityCtrl',
    };
  })
  .controller('titusServerGroupCapacitySelectorCtrl', function() {
  });

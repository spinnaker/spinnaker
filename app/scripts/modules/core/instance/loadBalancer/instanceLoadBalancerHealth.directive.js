'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.instance.loadBalancer.health.directive', [
])
  .directive('instanceLoadBalancerHealth', function() {
    return {
      restrict: 'E',
      scope: {
        loadBalancer: '=',
      },
      templateUrl: require('./health.html'),
      link: function(scope) {
        scope.healthState = scope.loadBalancer.healthState || (scope.loadBalancer.state === 'InService' ? 'Up' : 'OutOfService');
        scope.name = scope.loadBalancer.name || scope.loadBalancer.loadBalancerName;
      }
    };
  });

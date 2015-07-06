'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.instance.loadBalancer.health.directive', [
])
  .directive('instanceLoadBalancerHealth', function() {
    return {
      restrict: 'E',
      scope: {
        loadBalancer: '=',
      },
      templateUrl: require('./health.html'),
      link: function(scope) {
        scope.name = scope.loadBalancer.name || scope.loadBalancer.loadBalancerName;
      }
    };
  }).name;

'use strict';

angular.module('deckApp.instance.loadBalancer.health.directive', [])
  .directive('instanceLoadBalancerHealth', function() {
    return {
      restrict: 'E',
      scope: {
        loadBalancer: '=',
      },
      templateUrl: 'scripts/modules/instance/loadBalancer/health.html',
      link: function(scope) {
        scope.name = scope.loadBalancer.name || scope.loadBalancer.loadBalancerName;
      }
    };
  });

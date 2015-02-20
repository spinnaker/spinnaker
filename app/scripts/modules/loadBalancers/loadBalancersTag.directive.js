'use strict';


angular.module('deckApp.loadBalancer.tag', [])
  .directive('loadBalancersTag', function () {
    return {
      restrict: 'E',
      replace: false,
      templateUrl: 'scripts/modules/loadBalancers/loadBalancer/loadBalancersTag.html',
      scope: {
        loadBalancers: '=',
        maxDisplay: '='
      },
      link: function(scope) {
        scope.popover = { show: false };
      }
    };
  }
);

'use strict';


angular.module('spinnaker.loadBalancer.tag', [])
  .directive('loadBalancersTag', function () {
    return {
      restrict: 'E',
      replace: false,
      templateUrl: 'scripts/modules/loadBalancers/loadBalancer/loadBalancersTag.html',
      scope: {
        serverGroup: '=',
        maxDisplay: '='
      },
      link: function(scope) {
        scope.popover = { show: false };
      }
    };
  }
);

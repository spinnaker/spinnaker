'use strict';


angular.module('deckApp.securityGroup.counts', [])
  .directive('securityGroupCounts', function () {
    return {
      templateUrl: 'scripts/modules/securityGroups/securityGroupCounts.html',
      restrict: 'E',
      scope: {
        container: '='
      },
      link: function(scope) {
        var container = scope.container;

        scope.serverGroupCount = container.serverGroups ? container.serverGroups.length : 0;
        scope.loadBalancerCount = container.loadBalancers ? container.loadBalancers.length : 0;
      }
    };
  }
);

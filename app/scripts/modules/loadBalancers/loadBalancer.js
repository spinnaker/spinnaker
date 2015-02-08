'use strict';


angular.module('deckApp')
  .directive('loadBalancer', function ($rootScope, $timeout) {
    return {
      restrict: 'E',
      templateUrl: 'scripts/modules/loadBalancers/loadBalancer/loadBalancer.html',
      scope: {
        loadBalancer: '=',
        displayOptions: '='
      },
      link: function(scope, el) {
        var base = el.parent().inheritedData('$uiView').state;

        scope.$state = $rootScope.$state;

        scope.loadDetails = function(e) {
          $timeout(function() {
            var loadBalancer = scope.loadBalancer;
            // anything handled by ui-sref or actual links should be ignored
            if (e.isDefaultPrevented() || (e.originalEvent && e.originalEvent.target.href)) {
              return;
            }
            var params = {
              region: loadBalancer.region,
              accountId: loadBalancer.account,
              name: loadBalancer.name,
              vpcId: loadBalancer.vpcId,
              provider: loadBalancer.provider,
            };
            // also stolen from uiSref directive
            scope.$state.go('.loadBalancerDetails', params, {relative: base, inherit: true});
          });
        };

        scope.displayServerGroup = function (serverGroup) {
          if (scope.displayOptions.hideHealthy) {
            return serverGroup.downCount > 0;
          }
          return scope.displayOptions.showServerGroups;
        };
      }
    };
  });

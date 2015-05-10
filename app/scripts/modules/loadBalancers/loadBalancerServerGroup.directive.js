'use strict';


angular.module('deckApp.loadBalancer.serverGroup', [])
  .directive('loadBalancerServerGroup', function ($rootScope) {
    return {
      restrict: 'E',
      replace: true,
      templateUrl: 'scripts/modules/loadBalancers/loadBalancer/loadBalancerServerGroup.html',
      scope: {
        loadBalancer: '=',
        serverGroup: '=',
        displayOptions: '='
      },
      link: function (scope) {
        scope.$state = $rootScope.$state;
      }
    };
  }
);

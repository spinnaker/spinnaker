'use strict';


angular.module('deckApp')
  .directive('loadBalancerServerGroup', function ($rootScope) {
    return {
      restrict: 'E',
      replace: true,
      templateUrl: 'views/application/loadBalancer/loadBalancerServerGroup.html',
      scope: {
        serverGroup: '=',
        displayOptions: '='
      },
      link: function (scope) {
        scope.$state = $rootScope.$state;
      }
    };
  }
);

'use strict';

angular.module('deckApp')
  .directive('loadBalancer', function() {
    return {
      restrict: 'E',
      templateUrl: 'views/application/loadBalancer/loadBalancer.html',
      scope: {
        loadBalancer: '='
      },
      link: function(scope) {
        scope.$state = scope.$parent.$state;
        scope.sortFilter = scope.$parent.sortFilter;
        scope.displayServerGroup = function(serverGroup) {
          if (!scope.sortFilter.showAsgs) {
            return false;
          }
          if (scope.sortFilter.hideHealthy) {
            return serverGroup.downCount > 0;
          }
          return true;
        };
      }
    };
  });

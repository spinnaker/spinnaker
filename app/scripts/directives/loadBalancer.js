'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .directive('loadBalancer', function ($rootScope) {
    return {
      restrict: 'E',
      templateUrl: 'views/application/loadBalancer/loadBalancer.html',
      scope: {
        loadBalancer: '=',
        displayOptions: '='
      },
      link: function (scope) {
        scope.$state = $rootScope.$state;
        scope.displayServerGroup = function (serverGroup) {
          if (scope.displayOptions.hideHealthy) {
            return serverGroup.downCount > 0;
          }
          return scope.displayOptions.showServerGroups;
        };
      }
    };
  }
);

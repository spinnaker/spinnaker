'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .directive('loadBalancerServerGroup', function () {
    return {
      restrict: 'E',
      replace: true,
      templateUrl: 'views/application/loadBalancer/loadBalancerServerGroup.html',
      scope: {
        serverGroup: '=',
        asgFilter: '='
      },
      link: function (scope) {
        scope.instanceDisplay = {
          displayed: false
        };
        scope.$state = scope.$parent.$state;
        scope.sortFilter = scope.$parent.sortFilter;
      }
    };
  }
);

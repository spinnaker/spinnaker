'use strict';

angular.module('deckApp')
  .directive('loadBalancerHeading', function() {
    return {
      restrict: 'E',
      replace: true,
      templateUrl: 'views/application/loadBalancer/loadBalancerHeading.html',
      scope: {
        group: '='
      },
      link: function(scope) {
        scope.$state = scope.$parent.$state;
      }
    };
  });

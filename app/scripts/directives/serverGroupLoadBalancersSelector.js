'use strict';

angular.module('deckApp')
  .directive('serverGroupLoadBalancersSelector', function() {
    return {
      restrict: 'E',
      scope: {
        command: '=',
      },
      templateUrl: 'views/application/modal/serverGroup/aws/serverGroupLoadBalancersDirective.html'
    }
  });

'use strict';

angular.module('deckApp.delivery.executionGroup.directive', [])
  .directive('executionGroup', function() {
    return {
      restrict: 'E',
      replace: true,
      scope: {
        executions: '=',
        grouping: '=',
        scale: '=',
        filter: '=',
        application: '=',
      },
      templateUrl: 'scripts/modules/delivery/executionGroup.html',
      controller: 'executionGroup as ctrl',
    };
  });

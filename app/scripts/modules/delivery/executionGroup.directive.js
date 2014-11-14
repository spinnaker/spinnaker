'use strict';

angular.module('deckApp.delivery')
  .directive('executionGroup', function() {
    return {
      restrict: 'E',
      replace: true,
      scope: {
        executions: '=',
        grouping: '=',
        scale: '=',
        filter: '=',
      },
      templateUrl: 'scripts/modules/delivery/executionGroup.html',
      controller: 'executionGroup as ctrl',
    };
  });

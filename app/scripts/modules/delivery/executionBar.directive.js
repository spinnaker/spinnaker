'use strict';

angular.module('deckApp.delivery')
  .directive('executionBar', function() {
    return {
      restrict: 'E',
      replace: true,
      scope: {
        execution: '=',
        filter: '=',
        scale: '=',
        executions: '=',
      },
      templateUrl: 'scripts/modules/delivery/executionBar.html',
      controller: 'executionBar as ctrl',
    };
  });

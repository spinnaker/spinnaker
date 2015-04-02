'use strict';

angular.module('deckApp.delivery.execution.directive',[])
  .directive('execution', function() {
    return {
      restrict: 'E',
      replace: true,
      scope: {
        execution: '=',
        filter: '=',
        scale: '=',
        executions: '=',
        application: '=',
      },
      templateUrl: 'scripts/modules/delivery/execution.html',
      controller: 'execution as ctrl',
    };
  });

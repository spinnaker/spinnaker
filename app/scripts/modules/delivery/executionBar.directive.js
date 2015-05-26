'use strict';

angular.module('spinnaker.delivery.executionBar.directive', [])
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

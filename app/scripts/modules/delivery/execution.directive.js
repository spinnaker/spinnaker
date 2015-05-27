'use strict';

angular.module('spinnaker.delivery.execution.directive',[])
  .directive('execution', function($location) {
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
      link: function(scope) {
        scope.$location = $location;
      }
    };
  });

'use strict';

angular.module('deckApp.delivery.executionGroupHeading.directive', [
  'deckApp.delivery.execution.triggers',
])
  .directive('executionGroupHeading', function() {
    return {
      restrict: 'E',
      replace: true,
      scope: {
        value: '=',
        scale: '=',
        filter: '=',
        executions: '=',
        configurations: '=',
        application: '=',
      },
      templateUrl: 'scripts/modules/delivery/executionGroupHeading.html',
      controller: 'executionGroupHeading as ctrl',
    };
  });

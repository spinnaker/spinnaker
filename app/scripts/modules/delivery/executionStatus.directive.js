'use strict';

angular.module('deckApp.delivery.executionStatus.directive', [])
  .directive('executionStatus', function() {
    return {
      restrict: 'E',
      scope: {
        execution: '=',
        filter: '=',
      },
      templateUrl: 'scripts/modules/delivery/executionStatus.html',
      controller: 'executionStatus as ctrl',
    };
  });

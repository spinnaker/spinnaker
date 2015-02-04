'use strict';

angular.module('deckApp.delivery.executionStatus.directive', [])
  .directive('executionStatus', function() {
    return {
      restrict: 'E',
      replace: true,
      scope: {
        execution: '=',
      },
      templateUrl: 'scripts/modules/delivery/executionStatus.html',
      controller: 'executionStatus as ctrl',
    };
  });

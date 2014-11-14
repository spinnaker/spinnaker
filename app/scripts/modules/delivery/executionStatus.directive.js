'use strict';

angular.module('deckApp.delivery')
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

'use strict';

angular.module('deckApp.delivery.executionDetails.directive', [])
  .directive('executionDetails', function() {
    return {
      restrict: 'E',
      replace: true,
      scope: {
        execution: '=',
      },
      templateUrl: 'scripts/modules/delivery/details/executionDetails.html',
      controller: 'executionDetails as ctrl',
    };
  });

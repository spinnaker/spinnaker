'use strict';

angular.module('deckApp.delivery')
  .directive('executionDetails', function() {
    return {
      restrict: 'E',
      replace: true,
      scope: {
        execution: '=',
      },
      templateUrl: 'scripts/modules/delivery/executionDetails.html',
      controller: 'executionDetails as ctrl',
    };
  });

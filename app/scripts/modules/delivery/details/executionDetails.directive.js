'use strict';

angular.module('spinnaker.delivery.executionDetails.directive', [])
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

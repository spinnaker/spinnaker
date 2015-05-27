'use strict';

angular.module('spinnaker.delivery.executionDetails.stageFailureMessage.directive', [])
  .directive('stageFailureMessage', function () {
    return {
      restrict: 'E',
      templateUrl: 'scripts/modules/delivery/stageFailureMessage/stageFailureMessage.html',
      scope: {
        isFailed: '=',
        message: '=',
      },
    };
  });

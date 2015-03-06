'use strict';

angular.module('deckApp.delivery.executionDetails.stageFailureMessage.directive', [])
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

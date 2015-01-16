'use strict';

angular.module('deckApp.pipelines.stages.core.executionStepDetails', [])
  .directive('executionStepDetails', function() {
    return {
      restrict: 'E',
      scope: {
        item: '='
      },
      templateUrl: 'scripts/modules/pipelines/config/stages/core/executionStepDetails.html'
    };
  });

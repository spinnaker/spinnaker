'use strict';

angular.module('spinnaker.pipelines.stages.core.executionStepDetails', [
  'spinnaker.pipelines.config',
])
  .directive('executionStepDetails', function() {
    return {
      restrict: 'E',
      scope: {
        item: '='
      },
      templateUrl: 'scripts/modules/pipelines/config/stages/core/executionStepDetails.html',
      controller: 'ExecutionStepDetailsCtrl',
      controllerAs: 'executionStepDetailsCtrl'
    };
  })
  .controller('ExecutionStepDetailsCtrl', function() {

  });


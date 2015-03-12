'use strict';

angular.module('deckApp.pipelines.stages.core.executionStepDetails', [
  'deckApp.pipelines.config',
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
  .controller('ExecutionStepDetailsCtrl', function(pipelineConfig) {

    this.getTaskLabel = function(stageType) {
      var stageConfig = pipelineConfig.getStageConfig(stageType);
      if (stageConfig && stageConfig.executionTaskLabelUrl) {
        return stageConfig.executionTaskLabelUrl;
      } else {
        return 'scripts/modules/pipelines/config/stages/core/taskLabel.html';
      }
    };
  });


'use strict';

angular.module('deckApp.executionDetails.controller', [
  'ui.router',
  'deckApp.pipelines.config'
])
  .controller('executionDetails', function($scope, $stateParams, $state, pipelineConfig) {
    var controller = this;

    function getCurrentStage() {
      return parseInt($stateParams.stage);
    }

    function getCurrentStep() {
      return parseInt($stateParams.step);
    }

    controller.close = function() {
      $state.go('^');
    };

    controller.toggleDetails = function(index) {
      var newStepDetails = getCurrentStep() === index ? null : index;
      $state.go('.', {
        stage: getCurrentStage(),
        step: newStepDetails,
      });
    };

    controller.isStageCurrent = function(index) {
      return index === getCurrentStage();
    };

    controller.isStepCurrent = function(index) {
      return index === getCurrentStep();
    };

    controller.closeDetails = function(){
      $state.go('.', { step: null });
    };

    controller.getException = function(stage) {
      if (stage.context) {
        if (stage.context.exception && stage.context.exception.details.errors.length) {
          return stage.context.exception.details.errors.join(', ');
        }
        if (stage.context['kato.tasks'] && stage.context['kato.tasks'].length) {
          var lastTask = stage.context['kato.tasks'][stage.context['kato.tasks'].length-1];
          return lastTask.exception ? lastTask.exception.message : null;
        }
      }
      return null;
    };

    controller.getDetailsSourceUrl = function() {
      if ($stateParams.step !== undefined) {
        var stageSummary = $scope.execution.stageSummaries[getCurrentStage()];
        if (stageSummary) {
          var step = stageSummary.stages[getCurrentStep()] || stageSummary.masterStage;
          $scope.stageSummary = stageSummary;
          $scope.stage = step;
          var stageConfig = pipelineConfig.getStageConfig(step.type);
          if (stageConfig) {
            return stageConfig.executionDetailsUrl || null;
          }
        }
      }
      return null;
    };

    controller.getSummarySourceUrl = function() {
      if ($stateParams.stage !== undefined) {
        var stageSummary = $scope.execution.stageSummaries[getCurrentStage()];
        if (stageSummary) {
          $scope.stageSummary = stageSummary;
          $scope.stage = stageSummary.stages[0];
          var stageConfig = pipelineConfig.getStageConfig(stageSummary.type);
          if (stageConfig && stageConfig.executionSummaryUrl) {
            return stageConfig.executionSummaryUrl;
          }
        }
      }
      return 'scripts/modules/pipelines/config/stages/core/executionSummary.html';

    };

    controller.getStepLabel = function(stageType) {
      var stageConfig = pipelineConfig.getStageConfig(stageType);
      if (stageConfig && stageConfig.executionStepLabelUrl) {
        return stageConfig.executionStepLabelUrl;
      } else {
        return 'scripts/modules/pipelines/config/stages/core/stepLabel.html';
      }
    };

  });

'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.executionDetails.controller', [
  require('angular-ui-router'),
  require('../../pipeline/config/pipelineConfigProvider.js')
])
  .controller('executionDetails', function($scope, $stateParams, $state, pipelineConfig) {
    var controller = this;

    controller.standalone = $scope.standalone || false;

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
      if (newStepDetails !== null) {
        $state.go('.', {
          stage: getCurrentStage(),
          step: newStepDetails,
        });
      }
    };

    controller.isStageCurrent = function(index) {
      return index === getCurrentStage();
    };

    controller.isStepCurrent = function(index) {
      return index === getCurrentStep();
    };

    controller.closeDetails = function() {
      $state.go('.', { step: null });
    };

    controller.getDetailsSourceUrl = function() {
      if ($stateParams.step !== undefined) {
        let stages = $scope.execution.stageSummaries || [];
        var stageSummary = stages[getCurrentStage()];
        if (stageSummary) {
          var step = stageSummary.stages[getCurrentStep()] || stageSummary.masterStage;
          $scope.stageSummary = stageSummary;
          $scope.stage = step;
          var stageConfig = pipelineConfig.getStageConfig(step);
          if (stageConfig) {
            return stageConfig.executionDetailsUrl || null;
          }
        }
      }
      return null;
    };

    controller.getSummarySourceUrl = function() {
      if ($stateParams.stage !== undefined) {
        let currentStage = getCurrentStage();
        let stages = $scope.execution.stageSummaries || [];
        let stageSummary = stages.length > currentStage ?
          stages[currentStage] :
          null;
        if (stageSummary) {
          $scope.stageSummary = stageSummary;
          $scope.stage = stageSummary.stages[0];
          var stageConfig = pipelineConfig.getStageConfig(stageSummary);
          if (stageConfig && stageConfig.executionSummaryUrl) {
            return stageConfig.executionSummaryUrl;
          }
        }
      }
      return require('../../pipeline/config/stages/core/executionSummary.html');

    };

    controller.getStepLabel = function(stage) {
      var stageConfig = pipelineConfig.getStageConfig(stage);
      if (stageConfig && stageConfig.executionStepLabelUrl) {
        return stageConfig.executionStepLabelUrl;
      } else {
        return require('../../pipeline/config/stages/core/stepLabel.html');
      }
    };

    controller.isRestartable = function(stage) {
      var stageConfig = pipelineConfig.getStageConfig(stage);
      if (!stageConfig || stage.isRestarting === true || $scope.execution.isRunning) {
        return false;
      }

      return stageConfig.restartable || false;
    };

  });

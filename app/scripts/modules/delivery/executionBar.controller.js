'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.delivery.executionBar.controller', [
  require('../pipelines/config/pipelineConfigProvider.js'),
  require('angular-ui-router'),
])
  .controller('executionBar', function($scope, $filter, $stateParams, pipelineConfig, $state) {
    var controller = this;

    controller.getStageWidth = function() {
      return 100 / $scope.execution.stageSummaries.length + '%';
    };

    controller.getStageColor = function(stage) {
      var stageConfig = pipelineConfig.getStageConfig(stage.type);
      if (stageConfig.executionBarColorProvider && stageConfig.executionBarColorProvider(stage)) {
        return stageConfig.executionBarColorProvider(stage);
      }

      return $scope.scale[$scope.filter.stage.colorOverlay](
        stage[$scope.filter.stage.colorOverlay].toLowerCase()
      );
    };

    controller.getStageOpacity = function(stage) {
      if (!!$scope.filter.stage.solo.facet &&
        stage[$scope.filter.stage.solo.facet].toLowerCase() !==
        $scope.filter.stage.solo.value) {
        return 0.5;
      } else {
        return 0.8;
      }
    };

    controller.showingDetails = function(stage) {
      var param = $stateParams.stage ? parseInt($stateParams.stage) : 0;
      return $scope.execution.id === $stateParams.executionId && $scope.execution.stageSummaries.indexOf(stage) === param;
    };

    controller.styleStage = function(stage) {
      return {
        'background-color': controller.getStageColor(stage),
        opacity: controller.getStageOpacity(stage),
      };
    };

    controller.toggleDetails = function(executionId, stageIndex) {
      var stageSummary = $scope.execution.stageSummaries[stageIndex],
          masterIndex = stageSummary.masterStageIndex;
      if ($state.includes('**.execution', {executionId: executionId, stage: stageIndex})) {
        $state.go('^');
      } else {
        if ($state.includes('**.execution')) {
          $state.go('^.execution', {executionId: executionId, stage: stageIndex, step: masterIndex});
        } else {
          $state.go('.execution', {executionId: executionId, stage: stageIndex, step: masterIndex});
        }
      }
    };

    controller.getLabelTemplate = function(stage) {
      var target = stage.masterStage || stage;
      var config = pipelineConfig.getStageConfig(target.type);
      if (config && config.executionLabelTemplateUrl) {
        return config.executionLabelTemplateUrl;
      } else {
        return 'scripts/modules/pipelines/config/stages/core/executionBarLabel.html';
      }
    };

  }).name;

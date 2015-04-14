'use strict';

angular.module('deckApp.delivery.executionBar.controller', [
  'deckApp.pipelines.config',
  'ui.router',
])
  .controller('executionBar', function($scope, $filter, $stateParams, pipelineConfig) {
    var controller = this;

    controller.getStageWidth = function() {
      return 100 / $scope.execution.stageSummaries.length + '%';
    };

    controller.getStageColor = function(stage) {
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

    controller.getLabelTemplate = function(stage) {
      var target = stage.masterStage || stage;
      var config = pipelineConfig.getStageConfig(target.type);
      if (config && config.executionLabelTemplateUrl) {
        return config.executionLabelTemplateUrl;
      } else {
        return 'scripts/modules/pipelines/config/stages/core/executionBarLabel.html';
      }
    };

  });

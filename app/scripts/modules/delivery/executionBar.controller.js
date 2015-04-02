'use strict';

angular.module('deckApp.delivery.executionBar.controller', [
  'deckApp.pipelines.config',
])
  .controller('executionBar', function($scope, $filter, pipelineConfig) {
    var controller = this;

    controller.getStageWidth = function() {
      return 100 / $scope.filter.stage.max + '%';
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

    controller.styleStage = function(stage) {
      return {
        width: controller.getStageWidth(stage),
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

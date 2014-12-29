'use strict';

angular.module('deckApp.delivery')
  .controller('executionDetails', function($scope, $stateParams, $state, pipelineConfig, _) {
    var controller = this;

    controller.close = function() {
      $state.go('home.applications.application.executions', {
        application: $stateParams.application,
      });
    };

    controller.toggleDetails = function(stage) {
      var newStageDetails = $stateParams.stage === stage.name ? null : stage.name;
      $state.go('.', {
        stage: newStageDetails
      });
    };

    controller.isStageCurrent = function(stage) {
      return stage.name === $stateParams.stage;
    };

    controller.closeDetails = function(){
      $state.go('.', { stage: null });
    };

    controller.getDetailsSourceUrl = function() {
      if ($stateParams.stage) {
        var stage = _.find($scope.execution.stages, { name: $stateParams.stage });
        if (stage) {
          $scope.stage = stage;
          pipelineConfig.getStageTypes();
          var stageConfig = _.find(pipelineConfig.getStageTypes(), {key: stage.type});
          if (stageConfig) {
            return stageConfig.executionDetailsUrl || null;
          }
        }
      }
      return null;

    };

  });


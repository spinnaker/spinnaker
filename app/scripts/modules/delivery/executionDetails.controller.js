'use strict';

angular.module('deckApp.delivery')
  .controller('executionDetails', function($scope, $stateParams, $state, pipelineConfig, _) {
    var controller = this;

    function getCurrentStage() {
      return parseInt($stateParams.stage);
    }

    controller.close = function() {
      $state.go('home.applications.application.executions', {
        application: $stateParams.application,
      });
    };

    controller.toggleDetails = function(index) {
      var newStageDetails = getCurrentStage() === index ? null : index;
      $state.go('.', {
        stage: newStageDetails
      });
    };

    controller.isStageCurrent = function(index) {
      return index === getCurrentStage();
    };

    controller.closeDetails = function(){
      $state.go('.', { stage: null });
    };

    controller.getKatoException = function(stage) {
      if (stage.context && stage.context['kato.tasks'] && stage.context['kato.tasks'].length) {
        var lastTask = stage.context['kato.tasks'][stage.context['kato.tasks'].length-1];
        return lastTask.exception ? lastTask.exception.message : null;
      }
      return null;
    };

    controller.getDetailsSourceUrl = function() {
      if ($stateParams.stage) {
        var stage = $scope.execution.stages[getCurrentStage()];
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


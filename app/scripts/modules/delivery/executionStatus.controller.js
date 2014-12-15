'use strict';

angular.module('deckApp.delivery')
  .controller('executionStatus', function() {
    var controller = this;

    controller.getFailedStage = function(execution) {
      return execution.stages.filter(function(stage) {
        return stage.isFailed;
      })[0].name;
    };

    controller.getRunningStage = function(execution) {
      return execution.stages.filter(function(stage) {
        return stage.isRunning;
      })[0].name;
    };
  });

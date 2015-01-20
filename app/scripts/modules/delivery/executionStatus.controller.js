'use strict';

angular.module('deckApp.delivery')
  .controller('executionStatus', function() {
    var controller = this;

    controller.getFailedStage = function(execution) {
      var failed = execution.stages.filter(function(stage) {
        return stage.isFailed;
      });
      if (failed && failed.length) {
        return failed[0].name;
      }
      return 'Unknown';
    };

    controller.getRunningStage = function(execution) {
      var runningOrNext = execution.stages.filter(function(stage) {
        return stage.isRunning || stage.hasNotStarted;
      });
      if (runningOrNext && runningOrNext.length) {
        return runningOrNext[0].name;
      }
      return 'Unknown';
    };
  });

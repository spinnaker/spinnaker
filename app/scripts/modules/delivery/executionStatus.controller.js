'use strict';

angular.module('deckApp.delivery')
  .controller('executionStatus', function() {
    var controller = this;

    controller.getFailedStage = function(execution) {
      var failed = execution.stageSummaries.filter(function(stage) {
        return stage.isFailed;
      });
      if (failed && failed.length) {
        return failed[0].name;
      }
      return 'Unknown';
    };

    controller.getRunningStage = function(execution) {
      var runningOrNext = execution.stageSummaries.filter(function(stage) {
        return stage.isRunning || stage.hasNotStarted;
      });
      if (runningOrNext && runningOrNext.length) {
        return _.pluck(runningOrNext, 'name').join(', ');
      }
      return 'Unknown';
    };
  });

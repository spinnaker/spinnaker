'use strict';

angular.module('spinnaker.delivery.executionStatus.controller', [])
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
      var currentStages = execution.stageSummaries.filter(function(stage) {
        return stage.isRunning;
      });
      // if there are no running stages, find the first enqueued stage
      if (!currentStages.length) {
        var enqueued = execution.stageSummaries.filter(function(stage) {
          return stage.hasNotStarted;
        });
        if (enqueued && enqueued.length) {
          currentStages = [enqueued[0]];
        }
      }
      if (currentStages && currentStages.length) {
        return _.pluck(currentStages, 'name').join(', ');
      }
      return 'Unknown';
    };

    controller.getSuspendedStage = function(execution) {
      var suspended = execution.stageSummaries.filter(function(stage) {
        return stage.isSuspended;
      });
      if (suspended && suspended.length) {
        return suspended[0].name;
      }
      return 'Unknown';
    };

  });

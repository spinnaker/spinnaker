'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.delivery.executionStatus.controller', [
  require('../../utils/lodash.js'),
])
  .controller('executionStatus', function(_) {
    var controller = this;

    controller.getFailedStage = function(execution) {
      let stages = execution.stageSummaries || [];
      var failed = stages.filter(function(stage) {
        return stage.isFailed;
      });
      if (failed && failed.length) {
        return failed[0].name;
      }
      return 'Unknown';
    };

    controller.getRunningStage = function(execution) {
      let stages = execution.stageSummaries || [];
      var currentStages = stages.filter(function(stage) {
        return stage.isRunning;
      });
      // if there are no running stages, find the first enqueued stage
      if (!currentStages.length) {
        var enqueued = stages.filter(function(stage) {
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
      let stages = execution.stageSummaries || [];
      var suspended = stages.filter(function(stage) {
        return stage.isSuspended;
      });
      if (suspended && suspended.length) {
        return suspended[0].name;
      }
      return 'Unknown';
    };

  });

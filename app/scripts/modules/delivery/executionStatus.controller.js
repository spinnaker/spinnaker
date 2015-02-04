'use strict';

angular.module('deckApp.delivery.executionStatus.controller', [
  'deckApp.delivery.executions.service',
  'deckApp.confirmationModal.service'
])
  .controller('executionStatus', function(executionsService, confirmationModalService) {
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
      if (!currentStages) {
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

    controller.cancelExecution = function(execution) {
      confirmationModalService.confirm({
        header: 'Really stop execution of ' + execution.name + '?',
        buttonText: 'Stop running ' + execution.name,
        destructive: false,
        submitMethod: function() {
          return executionsService.cancelExecution(execution.id);
        }
      });

    };
  });

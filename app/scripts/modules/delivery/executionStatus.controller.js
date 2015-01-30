'use strict';

angular.module('deckApp.delivery')
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
      var runningOrNext = execution.stageSummaries.filter(function(stage) {
        return stage.isRunning || stage.hasNotStarted;
      });
      if (runningOrNext && runningOrNext.length) {
        return _.pluck(runningOrNext, 'name').join(', ');
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

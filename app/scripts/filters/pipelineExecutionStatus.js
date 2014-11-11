'use strict';

angular.module('deckApp')
  .filter('pipelineExecutionStatus', function() {
    return function(execution) {
      function getStageMatching(execution, status) {
        return execution.stages.filter(function(stage) {
          return stage.status === status;
        }).map(function(stage) {
          return stage.name;
        });
      }

      switch (execution.status) {
        case 'COMPLETED':
          return 'COMPLETED';
        case 'FAILED':
        case 'STOPPED':
          return getStageMatching(execution, 'FAILED')[0];
        case 'STARTED':
          return getStageMatching(execution, 'RUNNING')[0];
        case 'RUNNING':
          return getStageMatching(execution, 'RUNNING')[0];
      }
    };
  });

'use strict';

angular
  .module('deckApp.executionFilter.service', [])
  .factory('executionFilterService', function () {

    function filterRunningExecutions(executions) {
      if(executions) {
        return _.filter(executions, function(exe){
          return exe.isRunning || exe.hasNotStarted;
        });
      } else {
        return [];
      }
    }

    return {
      filterRunningExecutions: filterRunningExecutions
    };

  });

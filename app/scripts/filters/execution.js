'use strict';

angular.module('deckApp')
  .filter('executionFilter', function() {
    return function(executions) {
      executions.forEach(function(execution) {
        angular.noop(execution);
      });
      return executions;
    };
  });

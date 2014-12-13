'use strict';

angular.module('deckApp.delivery')
  .filter('executionGroups', function(timeBoundaries) {
    return function(executions, filter) {
      executions = executions || [];
      switch (filter.execution.groupBy) {
        case 'timeBoundary':
          return Object.keys(timeBoundaries.groupByTimeBoundary(executions));
        default:
          return Object.keys(executions.reduce(function(acc, execution) {
            acc[execution[filter.execution.groupBy]] = true;
            return acc;
          }, {}));
      }
    };
  });

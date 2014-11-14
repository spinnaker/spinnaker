'use strict';

angular.module('deckApp.delivery')
  .filter('executions', function(timeBoundaries) {
    return function(executions, filter, grouping) {
      return executions
        .filter(function(execution) {
          return [
            // execution status is not filtered
            filter.execution.status[execution.status.toLowerCase()],

            // not grouped
            !filter.execution.groupBy ||
            // grouped by execution property & execution is within current grouping
            (filter.execution.groupBy && filter.execution.groupBy !== 'timeBoundary' &&
              execution[filter.execution.groupBy] === grouping) ||
            // grouped by time boundary & execution is within current time boundary
            (filter.execution.groupBy && filter.execution.groupBy === 'timeBoundary' &&
              timeBoundaries.isContainedWithin(execution, grouping)),
          ].every(function(condition) {
            return condition;
          });
        })
        .sort(function(a, b) {
          return b[filter.execution.sortBy] - a[filter.execution.sortBy];
        });
    };
  });

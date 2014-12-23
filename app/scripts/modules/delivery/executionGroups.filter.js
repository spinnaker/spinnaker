'use strict';

angular.module('deckApp.delivery')
  .filter('executionGroups', function(timeBoundaries) {
    return function(executions, filter, configurations) {
      switch (filter.execution.groupBy) {
        case 'timeBoundary':
          return Object.keys(timeBoundaries.groupByTimeBoundary(executions));
        default:
          configurations = configurations || [];
          return _.unique(_.pluck(executions.concat(configurations), filter.execution.groupBy)).sort();
      }
    };
  });

'use strict';

angular.module('deckApp.delivery.executionGroups.filter', [
  'deckApp.utils.lodash',
  'deckApp.timeBoundaries.service'
])
  .filter('executionGroups', function(timeBoundaries, _) {
    return function(executions, filter, configurations) {
      switch (filter.execution.groupBy) {
        case 'timeBoundary':
          return Object.keys(timeBoundaries.groupByTimeBoundary(executions));
        case 'name':
          configurations = configurations || [];
          return _.unique(_.pluck(_.sortBy(executions, 'index').concat(configurations), filter.execution.groupBy));
        default:
          configurations = configurations || [];
          return _.unique(_.pluck(executions.concat(configurations), filter.execution.groupBy)).sort();
      }
    };
  });

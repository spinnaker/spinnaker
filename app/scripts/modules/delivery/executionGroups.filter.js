'use strict';

angular.module('spinnaker.delivery.executionGroups.filter', [
  'spinnaker.utils.lodash',
  'spinnaker.timeBoundaries.service'
])
  .filter('executionGroups', function(timeBoundaries, _) {
    return function(executions, filter, configurations) {
      switch (filter.execution.groupBy) {
        case 'timeBoundary':
          return Object.keys(timeBoundaries.groupByTimeBoundary(executions));
        case 'name':
          configurations = configurations || [];
          return _.unique(_.pluck(_.sortBy(configurations, 'index').concat(executions), filter.execution.groupBy));
        default:
          configurations = configurations || [];
          return _.unique(_.pluck(executions.concat(configurations), filter.execution.groupBy)).sort();
      }
    };
  });

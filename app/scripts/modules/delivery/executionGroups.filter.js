'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.delivery.executionGroups.filter', [
  require('../utils/lodash.js'),
  require('./timeBoundaries.service.js')
])
  .filter('executionGroups', function(timeBoundaries, _) {
    return function(executions, filter, configurations=[]) {
      switch (filter.execution.groupBy) {
        case 'timeBoundary':
          return Object.keys(timeBoundaries.groupByTimeBoundary(executions));
        case 'name':
          return _.unique(_.pluck(_.sortBy(configurations, 'index').concat(executions), filter.execution.groupBy));
        default:
          return _.unique(_.pluck(executions.concat(configurations), filter.execution.groupBy)).sort();
      }
    };
  }).name;

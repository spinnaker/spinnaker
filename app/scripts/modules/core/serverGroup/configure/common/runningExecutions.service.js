'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.executionFilter.service', [
    require('../../../utils/lodash.js'),
  ])
  .factory('runningExecutionsService', function(_) {

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

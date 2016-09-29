'use strict';

import _ from 'lodash';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.executionFilter.service', [])
  .factory('runningExecutionsService', function () {

    function filterRunningExecutions(executions) {
      if(executions) {
        return _.filter(executions, function(exe) {
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

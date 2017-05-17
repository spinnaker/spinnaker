'use strict';

const angular = require('angular');

import { EXECUTION_FILTER_MODEL } from 'core/delivery/filter/executionFilter.model';
import { EXECUTION_FILTER_SERVICE } from 'core/delivery/filter/executionFilter.service';

import './executionFilter.less';

module.exports = angular
  .module('spinnaker.core.delivery.filter.executionFilters.directive', [
    require('./executionFilter.controller.js'),
    EXECUTION_FILTER_SERVICE,
    EXECUTION_FILTER_MODEL,
  ])
  .directive('executionFilters', function() {
    return {
      restrict: 'E',
      templateUrl: require('./filterNav.html'),
      scope: {},
      bindToController: {
        application: '=',
      },
      controller: 'ExecutionFilterCtrl',
      controllerAs: 'vm',
    };
  });

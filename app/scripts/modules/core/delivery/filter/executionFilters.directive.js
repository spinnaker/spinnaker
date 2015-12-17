'use strict';

let angular = require('angular');

require('./executionFilter.less');

module.exports = angular
  .module('spinnaker.core.delivery.filter.executionFilters.directive', [
    require('./executionFilter.controller.js'),
    require('./executionFilter.service.js'),
    require('./executionFilter.model.js'),
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

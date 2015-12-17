'use strict';

let angular = require('angular');

require('./executionStatus.less');

module.exports = angular.module('spinnaker.core.delivery.executionStatus.directive', [
  require('../filter/executionFilter.model.js'),
])
  .directive('executionStatus', function(ExecutionFilterModel) {
    return {
      restrict: 'E',
      scope: {
        execution: '=',
        filter: '=',
      },
      templateUrl: require('./executionStatus.html'),
      controller: 'executionStatus as ctrl',
      link: function(scope) {
        scope.filter = ExecutionFilterModel.sortFilter;
      }
    };
  });

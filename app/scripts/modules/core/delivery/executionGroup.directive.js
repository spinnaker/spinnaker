'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.delivery.executionGroup.directive', [
])
  .directive('executionGroup', function() {
    return {
      restrict: 'E',
      replace: true,
      scope: {
        executions: '=',
        grouping: '=',
        scale: '=',
        filter: '=',
        application: '=',
      },
      templateUrl: require('./executionGroup.html'),
      controller: 'executionGroup as ctrl',
    };
  }).name;

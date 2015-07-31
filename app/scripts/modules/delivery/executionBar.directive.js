'use strict';

let angular = require('angular');

require('./executionBar.html');

module.exports = angular.module('spinnaker.delivery.executionBar.directive', [
])
  .directive('executionBar', function() {
    return {
      restrict: 'E',
      replace: true,
      scope: {
        execution: '=',
        filter: '=',
        scale: '=',
        executions: '=',
      },
      templateUrl: require('./executionBar.html'),
      controller: 'executionBar as ctrl',
    };
  }).name;

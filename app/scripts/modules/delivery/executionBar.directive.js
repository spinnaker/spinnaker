'use strict';

let angular = require('angular');

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
      template: require('./executionBar.html'),
      controller: 'executionBar as ctrl',
    };
  });

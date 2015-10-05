'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.delivery.executionStatus.directive', [
  require('../../utils/lodash.js'),
])
  .directive('executionStatus', function(_) {
    return {
      restrict: 'E',
      scope: {
        execution: '=',
        filter: '=',
      },
      templateUrl: require('./executionStatus.html'),
      controller: 'executionStatus as ctrl',
    };
  }).name;

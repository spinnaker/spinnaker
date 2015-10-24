'use strict';

let angular = require('angular');

require('./executionDetails.less');

module.exports = angular.module('spinnaker.core.delivery.executionDetails.directive', [
])
  .directive('executionDetails', function() {
    return {
      restrict: 'E',
      scope: {
        execution: '=',
      },
      templateUrl: require('./executionDetails.html'),
      controller: 'executionDetails as ctrl',
    };
  }).name;

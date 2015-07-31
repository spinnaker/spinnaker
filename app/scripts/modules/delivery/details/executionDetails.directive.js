'use strict';

let angular = require('angular');

require('./executionDetails.html');

module.exports = angular.module('spinnaker.delivery.executionDetails.directive', [
])
  .directive('executionDetails', function() {
    return {
      restrict: 'E',
      replace: true,
      scope: {
        execution: '=',
      },
      templateUrl: require('./executionDetails.html'),
      controller: 'executionDetails as ctrl',
    };
  }).name;

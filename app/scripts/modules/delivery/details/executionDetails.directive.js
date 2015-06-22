'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.delivery.executionDetails.directive', [
])
  .directive('executionDetails', function() {
    return {
      restrict: 'E',
      replace: true,
      scope: {
        execution: '=',
      },
      template: require('./executionDetails.html'),
      controller: 'executionDetails as ctrl',
    };
  });

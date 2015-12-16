'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.delivery.execution.build.number.directive', [])
  .directive('executionBuildNumber', function() {
    return {
      restrict: 'E',
      scope: {
        execution: '='
      },
      templateUrl: require('./executionBuildNumber.directive.html'),
    };
  });

'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.executionWindows.directive', [
  require('./executionWindows.controller.js'),
])
  .directive('executionWindows', function() {
    return {
      restrict: 'E',
      scope: {
        stage: '='
      },
      templateUrl: require('./executionWindows.html'),
      controller: 'ExecutionWindowsCtrl',
      controllerAs: 'executionWindowsCtrl',
    };
  });

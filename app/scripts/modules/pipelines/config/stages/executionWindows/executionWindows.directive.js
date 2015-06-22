'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.pipelines.stage.executionWindows.directive', [
  require('./executionWindows.controller.js'),
])
  .directive('executionWindows', function() {
    return {
      restrict: 'E',
      scope: {
        stage: '='
      },
      template: require('./executionWindows.html'),
      controller: 'ExecutionWindowsCtrl',
      controllerAs: 'executionWindowsCtrl',
    };
  });

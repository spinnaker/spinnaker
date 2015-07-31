'use strict';

let angular = require('angular');

require('./executionWindows.html');

module.exports = angular.module('spinnaker.pipelines.stage.executionWindows.directive', [
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
  }).name;

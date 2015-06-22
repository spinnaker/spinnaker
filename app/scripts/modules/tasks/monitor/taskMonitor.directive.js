'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.tasks.monitor.directive', [])
  .directive('taskMonitor', function () {
    return {
      restrict: 'E',
      replace: true,
      template: require('./taskMonitor.html'),
      scope: {
        taskMonitor: '=monitor'
      }
    };
  }
);

'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.tasks.monitor.directive', [
  require('../../presentation/autoScroll/autoScroll.directive.js'),
  require('../../modal/modalOverlay.directive.js'),
  require('../../modal/buttons/modalClose.directive.js'),
])
  .directive('taskMonitor', function () {
    return {
      restrict: 'E',
      replace: true,
      templateUrl: require('./taskMonitor.html'),
      scope: {
        taskMonitor: '=monitor'
      }
    };
  }
);

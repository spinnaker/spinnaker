'use strict';

let angular = require('angular');

require('./taskMonitor.directive.less');

module.exports = angular.module('spinnaker.tasks.monitor.directive', [
  require('../../presentation/autoScroll/autoScroll.directive.js'),
  require('../../modal/modalOverlay.directive.js'),
  require('../../modal/buttons/modalClose.directive.js'),
  require('./taskMonitorError.component'),
  require('./taskMonitorStatus.component'),
])
  .directive('taskMonitor', function () {
    return {
      restrict: 'E',
      templateUrl: require('./taskMonitor.html'),
      scope: {
        taskMonitor: '=monitor'
      }
    };
  }
);

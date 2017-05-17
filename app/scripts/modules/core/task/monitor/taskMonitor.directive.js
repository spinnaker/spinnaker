'use strict';

const angular = require('angular');

import { MODAL_CLOSE_COMPONENT } from 'core/modal/buttons/modalClose.component';
import { AUTO_SCROLL_DIRECTIVE } from 'core/presentation/autoScroll/autoScroll.directive';

import './taskMonitor.directive.less';

module.exports = angular.module('spinnaker.tasks.monitor.directive', [
  AUTO_SCROLL_DIRECTIVE,
  require('../../modal/modalOverlay.directive.js'),
  MODAL_CLOSE_COMPONENT,
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

import { IComponentOptions, module } from 'angular';

import { AUTO_SCROLL_DIRECTIVE } from 'core/presentation/autoScroll/autoScroll.directive';
import { MODAL_CLOSE_COMPONENT } from 'core/modal/buttons/modalClose.component';

import './taskMonitor.directive.less';

export const TASKS_MONITOR_DIRECTIVE = 'spinnaker.tasks.monitor.directive';

const ngmodule = module(TASKS_MONITOR_DIRECTIVE, [
  AUTO_SCROLL_DIRECTIVE,
  require('../../modal/modalOverlay.directive').name,
  MODAL_CLOSE_COMPONENT,
  require('./taskMonitorError.component').name,
  require('./taskMonitorStatus.component').name,
]);

ngmodule.directive('taskMonitor', function() {
  return {
    restrict: 'E',
    templateUrl: require('./taskMonitor.html'),
    scope: {
      taskMonitor: '=monitor',
    },
  };
});

export const taskMonitorWrapperComponent: IComponentOptions = {
  template: `<task-monitor monitor="$ctrl.monitor"></task-monitor>`,
  bindings: {
    monitor: '<',
  }
};

ngmodule.component('taskMonitorWrapper', taskMonitorWrapperComponent);

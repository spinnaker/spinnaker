import { IComponentOptions, module } from 'angular';

import { AUTO_SCROLL_DIRECTIVE } from 'core/presentation/autoScroll/autoScroll.directive';
import { MODAL_CLOSE_COMPONENT } from 'core/modal/buttons/modalClose.component';
import { TASK_MONITOR_ERROR } from './TaskMonitorError';

import './taskMonitor.directive.less';
import { CORE_MODAL_MODALOVERLAY_DIRECTIVE } from '../../modal/modalOverlay.directive';
import { CORE_TASK_MONITOR_TASKMONITORSTATUS_COMPONENT } from './taskMonitorStatus.component';

export const TASKS_MONITOR_DIRECTIVE = 'spinnaker.tasks.monitor.directive';

const ngmodule = module(TASKS_MONITOR_DIRECTIVE, [
  AUTO_SCROLL_DIRECTIVE,
  CORE_MODAL_MODALOVERLAY_DIRECTIVE,
  MODAL_CLOSE_COMPONENT,
  TASK_MONITOR_ERROR,
  CORE_TASK_MONITOR_TASKMONITORSTATUS_COMPONENT,
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
  },
};

ngmodule.component('taskMonitorWrapper', taskMonitorWrapperComponent);

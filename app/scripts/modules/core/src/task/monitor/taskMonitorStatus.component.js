'use strict';

import { module } from 'angular';

export const CORE_TASK_MONITOR_TASKMONITORSTATUS_COMPONENT = 'spinnaker.core.task.monitor.status';
export const name = CORE_TASK_MONITOR_TASKMONITORSTATUS_COMPONENT; // for backwards compatibility
module(CORE_TASK_MONITOR_TASKMONITORSTATUS_COMPONENT, []).component('taskMonitorStatus', {
  bindings: {
    monitor: '=',
  },
  template: `<ul class="task task-progress">
          <li ng-repeat="step in $ctrl.monitor.task.steps | displayableTasks" ng-class="{'not-started': step.hasNotStarted}">
            <status-glyph item="step"></status-glyph>
            {{step.name | robotToHuman}}
            <span ng-if="step.startTime">({{step.runningTimeInMs | duration}})</span>
          </li>
        </ul>
        <ul class="task task-progress task-progress-running" ng-if="$ctrl.monitor.task.isActive">
          <li><loading-spinner size="'small'"></loading-spinner></li>
        </ul>
        <ul class="task task-progress task-progress-refresh" ng-if="$ctrl.monitor.task.isCompleted">
          <li>
            <span class="far fa-check-circle"></span> <strong>Operation succeeded!</strong>
          </li>
        </ul>
        <p ng-if="$ctrl.monitor.task.id && !$ctrl.monitor.error && $ctrl.monitor.application">
            You can
            <a ui-sref="home.applications.application.tasks.taskDetails({application: $ctrl.monitor.application.name, taskId: $ctrl.monitor.task.id})">monitor
              this task from the Tasks view</a>.
        </p>`,
});

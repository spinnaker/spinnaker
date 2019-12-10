'use strict';

import { module } from 'angular';

import './multiTaskMonitor.component.less';

export const CORE_TASK_MONITOR_MULTITASKMONITOR_COMPONENT = 'spinnaker.core.task.monitor.multiTaskMonitor';
export const name = CORE_TASK_MONITOR_MULTITASKMONITOR_COMPONENT; // for backwards compatibility
module(CORE_TASK_MONITOR_MULTITASKMONITOR_COMPONENT, []).component('multiTaskMonitor', {
  bindings: {
    monitors: '=',
    title: '=',
    closeModal: '&',
  },
  controller: function() {
    this.isVisible = () => this.monitors.some(monitor => monitor.submitting || monitor.error);
    this.hasErrors = () => this.monitors.some(monitor => monitor.error);
    this.clearErrors = () => this.monitors.forEach(monitor => (monitor.error = null));
  },
  template: `
      <div modal-page class="overlay overlay-modal" modal-overlay ng-if="$ctrl.isVisible()">
        <div class="modal-header">
          <h3>{{$ctrl.title}}</h3>
        </div>
        <div class="modal-body clearfix">
          <div class="clearfix">
            <div class="col-md-6 overlay-modal-status" ng-repeat="monitor in $ctrl.monitors">
              <h4>{{monitor.title}}</h4>
              <task-monitor-status monitor="monitor"></task-monitor-status>
              <task-monitor-error error-message="monitor.errorMessage" task="monitor.task"></task-monitor-error>
            </div>
          </div>
        </div>
        <div class="modal-footer" ng-if="!$ctrl.hasErrors()">
          <button class="btn btn-primary" ng-click="$ctrl.closeModal()" auto-focus>Close</button>
        </div>
        <div class="modal-footer" ng-if="$ctrl.hasErrors()">
          <button class="btn btn-primary" ng-click="$ctrl.clearErrors()">Go back and try to fix
            this
          </button>
          <button class="btn btn-default" ng-click="$ctrl.closeModal()">Cancel</button>
        </div>
      </div>
`,
});

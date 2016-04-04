'use strict';

const angular = require('angular');

module.exports = angular
    .module('spinnaker.core.task.monitor.error', [])
    .component('taskMonitorError', {
      bindings: {
        monitor: '=',
      },
      template: `<div class="col-md-12 overlay-modal-error" ng-if="$ctrl.monitor.error">
        <alert type="danger">
          <h4><span class="glyphicon glyphicon-warning-sign"></span> Error:</h4>

          <p>{{$ctrl.monitor.errorMessage}}</p>
        </alert>
        <p ng-if="$ctrl.monitor.task.id">
          <a
            ui-sref="home.applications.application.tasks.taskDetails({application: $ctrl.monitor.application.name, taskId: $ctrl.monitor.task.id})">
            View this failed task in the tasks pane.
          </a>
        </p>
      </div>`
    });

'use strict';

const angular = require('angular');

module.exports = angular
    .module('spinnaker.core.task.monitor.status', [])
    .component('taskMonitorStatus', {
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
          <li><span class="glyphicon glyphicon-spinning glyphicon-asterisk"></span></li>
        </ul>
        <ul class="task task-progress task-progress-refresh" ng-if="$ctrl.monitor.task.isCompleted">
          <li>
            <span class="fa fa-check-circle-o"></span> <strong>Operation succeeded!</strong>
          </li>
        </ul>
        <p ng-if="$ctrl.monitor.task.id && !$ctrl.monitor.error">
            You can
            <a ui-sref="home.applications.application.tasks.taskDetails({application: $ctrl.monitor.application.name, taskId: $ctrl.monitor.task.id})">monitor
              this task from the Tasks view</a>.
        </p>`
    });

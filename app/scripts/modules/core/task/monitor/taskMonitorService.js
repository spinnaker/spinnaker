'use strict';

import _ from 'lodash';
import {TASK_READ_SERVICE} from 'core/task/task.read.service';

const angular = require('angular');

module.exports = angular.module('spinnaker.tasks.monitor.service', [
  require('./taskMonitor.directive.js'),
  TASK_READ_SERVICE,
])
  .factory('taskMonitorService', function($log, taskReader, $timeout) {

    function buildTaskMonitor(params) {
      var monitor = {
        submitting: false,
        task: null,
        error: false,
        errorMessage: null,
        title: params.title,
        application: params.application,
        onTaskComplete: params.onTaskComplete || angular.noop,
        modalInstance: params.modalInstance,
        monitorInterval: params.monitorInterval || 1000,
        submitMethod: params.submitMethod,
      };

      monitor.onModalClose = function() {
        if (monitor.task && monitor.task.poller) {
          $timeout.cancel(monitor.task.poller);
        }
      };

      monitor.modalInstance.result.then(monitor.onModalClose, monitor.onModalClose);

      monitor.closeModal = function () {
        try {
          monitor.modalInstance.dismiss();
        } catch(e) {
          // modal was already closed
        }
      };

      monitor.startSubmit = function() {
        monitor.submitting = true;
        monitor.task = null;
        monitor.error = false;
        monitor.errorMessage = null;
      };

      monitor.setError = function(task) {
        if (task) {
          monitor.task = task;
          monitor.errorMessage = monitor.task.failureMessage || 'There was an unknown server error.';
        } else {
          monitor.errorMessage = 'There was an unknown server error.';
        }
        monitor.submitting = false;
        monitor.error = true;
        $log.warn('Error with task:', monitor.task);
      };

      monitor.handleTaskSuccess = function (task) {
        monitor.task = task;
        if (_.has(monitor, 'application.runningOrchestrations.refresh')) {
          monitor.application.runningOrchestrations.refresh();
        }
        taskReader.waitUntilTaskCompletes(task, monitor.monitorInterval)
          .then(monitor.onTaskComplete, monitor.setError);
      };

      monitor.submit = function(method) {
        monitor.startSubmit();
        let submit = monitor.submitMethod || method;
        submit().then(monitor.handleTaskSuccess, monitor.setError);
      };

      monitor.callPreconfiguredSubmit = (params) => {
        monitor.startSubmit();
        monitor.submitMethod(params).then(monitor.handleTaskSuccess, monitor.setError);
      };

      return monitor;
    }

    return {
      buildTaskMonitor: buildTaskMonitor
    };
  });

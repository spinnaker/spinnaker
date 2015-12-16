'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.tasks.monitor.service', [
  require('../../utils/lodash.js'),
  require('./taskMonitor.directive.js'),
])
  .factory('taskMonitorService', function($log, _) {

    /**
     * Either provide an onApplicationRefresh method OR an onTaskComplete method in the params!
     */
    function buildTaskMonitor(params) {
      var monitor = {
        submitting: false,
        forceRefreshing: false,
        forceRefreshEnabled: !!params.forceRefreshEnabled,
        forceRefreshComplete: false,
        task: null,
        error: false,
        errorMessage: null,
        title: params.title,
        forceRefreshMessage: params.forceRefreshMessage || null,
        application: params.application,
        onApplicationRefresh: params.onApplicationRefresh || angular.noop,
        onTaskComplete: params.onTaskComplete || angular.noop,
        modalInstance: params.modalInstance,
        katoPhaseToMonitor: params.katoPhaseToMonitor || null,
        hasKatoTask: _.isBoolean(params.hasKatoTask) ? params.hasKatoTask : true
      };

      monitor.onModalClose = function() {
        if (monitor.task && monitor.task.cancelPolls) {
          monitor.task.cancelPolls();
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
        monitor.forceRefreshing = false;
        monitor.task = null;
        monitor.error = false;
        monitor.errorMessage = null;
      };

      monitor.setError = function(task) {
        if (task) {
          monitor.task = task;
        }
        monitor.submitting = false;
        monitor.error = true;
        monitor.errorMessage = monitor.task.failureMessage || monitor.task.lastKatoMessage || 'There was an unknown server error.';
        $log.warn('Error with task:', monitor.task);
      };

      monitor.startForceRefresh = function() {
        monitor.forceRefreshing = true;
      };

      monitor.handleTaskSuccess = function (task) {
        monitor.task = task;
        if(monitor.hasKatoTask) {
          task.getCompletedKatoTask(monitor.katoPhaseToMonitor).then(
            function () {
              processSuccessfulTask(task);
            },
            handleKatoFailure
          );
        } else {
          processSuccessfulTask(task);
        }
      };

      monitor.submit = function(method) {
        monitor.startSubmit();
        method.call().then(monitor.handleTaskSuccess, monitor.setError);
      };

      function processSuccessfulTask(task) {
        task.get().then(function() {
          if (monitor.forceRefreshEnabled) {
            task.watchForForceRefresh().then(handleForceRefreshComplete, monitor.setError);
          } else {
            monitor.forceRefreshComplete = true;
            task.watchForTaskComplete().then(monitor.onTaskComplete, monitor.setError);
          }
        });
      }

      function handleForceRefreshComplete() {
        monitor.startForceRefresh();
        monitor.application.registerOneTimeRefreshHandler(handleApplicationRefreshComplete);
        monitor.application.refreshImmediately();
      }

      function handleApplicationRefreshComplete() {
        monitor.forceRefreshing = false;
        monitor.forceRefreshComplete = true;
        monitor.onApplicationRefresh.call();
      }

      function handleKatoFailure(katoTask) {
        monitor.task.updateKatoTask(katoTask);
        monitor.setError();
      }

      return monitor;

    }

    return {
      buildTaskMonitor: buildTaskMonitor
    };
  });

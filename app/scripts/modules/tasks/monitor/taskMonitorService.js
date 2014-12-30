'use strict';


angular.module('deckApp.tasks.monitor')
  .factory('taskMonitorService', function($exceptionHandler) {

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
        katoPhaseToMonitor: params.katoPhaseToMonitor || null
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
        $exceptionHandler('Error with task:', monitor.task);
      };

      monitor.startForceRefresh = function() {
        monitor.forceRefreshing = true;
      };

      monitor.handleTaskSuccess = function(task) {
        monitor.task = task;
        task.getCompletedKatoTask(monitor.katoPhaseToMonitor).then(
          function() {
            handleKatoRefreshSuccess(task);
          },
          handleKatoFailure
        );
      };

      monitor.submit = function(method) {
        monitor.startSubmit();
        method.call().then(monitor.handleTaskSuccess, monitor.setError);
      };

      function handleKatoRefreshSuccess(task) {
        task.get().then(function() {
          if (monitor.forceRefreshEnabled) {
            task.watchForForceRefresh().then(handleForceRefreshComplete, handleApplicationRefreshComplete);
          } else {
            monitor.forceRefreshComplete = true;
            task.watchForTaskComplete().then(monitor.onTaskComplete, monitor.setError);
          }
        });
      }

      function handleForceRefreshComplete() {
        monitor.startForceRefresh();
        monitor.application.refreshImmediately().then(handleApplicationRefreshComplete);
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

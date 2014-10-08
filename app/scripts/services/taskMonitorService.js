'use strict';

var angular = require('angular');

angular.module('deckApp')
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
        onApplicationRefresh: params.onApplicationRefresh || params.modalInstance.dismiss,
        onTaskComplete: params.onTaskComplete || params.modalInstance.dismiss,
        modalInstance: params.modalInstance
      };

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
        monitor.task = task;
        monitor.submitting = false;
        monitor.error = true;
        monitor.errorMessage = task.failureMessage || 'There was an unknown server error.';
        $exceptionHandler('Error with task:', task);
      };

      monitor.startForceRefresh = function() {
        monitor.forceRefreshing = true;
      };

      monitor.handleTaskSuccess = function(task) {
        monitor.task = task;
        task.getCompletedKatoTask().then(
          function() {
            handleKatoRefreshSuccess(task);
          },
          handleKatoFailure,
          handleTaskNotification
        );
      };

      monitor.handleTaskError = function(task) {
        monitor.setError(task);
      };

      monitor.submit = function(method) {
        monitor.startSubmit();
        method.call().then(monitor.handleTaskSuccess, monitor.handleTaskError);
      };

      function handleKatoRefreshSuccess(task) {
        task.get().then(function(updatedTask) {
          monitor.task = updatedTask;
          if (monitor.forceRefreshEnabled) {
            updatedTask.watchForForceRefresh().then(handleForceRefreshComplete, handleApplicationRefreshComplete);
          } else {
            monitor.forceRefreshComplete = true;
            updatedTask.watchForTaskComplete().then(monitor.onTaskComplete, monitor.handleTaskError);
          }
        });
      }

      function handleForceRefreshComplete(task) {
        monitor.task = task;
        monitor.startForceRefresh();
        monitor.application.refreshImmediately().then(handleApplicationRefreshComplete);
      }

      function handleApplicationRefreshComplete() {
        monitor.forceRefreshComplete = true;
        monitor.onApplicationRefresh.call();
      }

      function handleKatoFailure(task) {
        monitor.setError(task);
      }

      function handleTaskNotification(task) {
        monitor.task = task;
      }

      return monitor;

    }

    return {
      buildTaskMonitor: buildTaskMonitor
    };
  });

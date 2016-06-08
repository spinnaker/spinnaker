'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.orchestratedItem.transformer', [
  require('../utils/moment.js')
])
  .factory('orchestratedItemTransformer', function(momentService, $log) {

    function getOrchestrationException(task) {
      var katoTasks = task.getValueFor('kato.tasks');
      if (katoTasks && katoTasks.length) {
        var steps = katoTasks[katoTasks.length - 1].history;
        var exception = katoTasks[katoTasks.length - 1].exception;
        if (exception) {
          return exception.message;
        }
        if (steps && steps.length) {
          return steps[steps.length - 1].status;
        }
      }
      return null;
    }

    function getGeneralException(task) {
      var generalException = task.getValueFor('exception');
      if (generalException) {
        if (generalException.details && generalException.details.errors && generalException.details.errors.length) {
          return generalException.details.errors.join(', ');
        }
        if (generalException.details && generalException.details.error) {
          return generalException.details.error;
        }
      }
      return null;
    }

    function getMigrationException(task) {
      let tideTask = task.getValueFor('tide.task');
      if (tideTask && tideTask.taskComplete && tideTask.taskComplete.status === 'failure') {
        return tideTask.taskComplete.message;
      }
      return null;
    }

    function calculateRunningTime(item) {
      return function() {
        let normalizedNow = Math.max(new Date().getTime(), item.startTime);
        return (parseInt(item.endTime) || normalizedNow) - parseInt(item.startTime);
      };
    }

    function defineProperties(item) {
      if (!item || typeof item !== 'object') {
        return;
      }
      var testDescriptor = Object.getOwnPropertyDescriptor(item, 'runningTime');
      if (testDescriptor && !testDescriptor.enumerable) {
        return;
      }

      item.getValueFor = function(key) {
        if (item.context) {
          return item.context[key];
        }
        if (!item.variables) {
          return null;
        }
        var [matching] = item.variables.filter((item) => item.key === key).map((variable) => variable.value);
        return matching;
      };

      item.originalStatus = item.status;
      Object.defineProperties(item, {
        failureMessage: {
          get: function() {
            return getGeneralException(item) || getMigrationException(item) || getOrchestrationException(item) || null;
          }
        },
        isCompleted: {
          get: function() {
            return item.status === 'SUCCEEDED' || item.status === 'SKIPPED';
          },
        },
        isRunning: {
          get: function() {
            return item.status === 'RUNNING';
          },
        },
        isFailed: {
          get: function() {
            return item.status === 'TERMINAL';
          },
        },
        isActive: {
          get: function() {
            return item.status === 'RUNNING' || item.status === 'SUSPENDED' || item.status === 'NOT_STARTED' || item.status === 'PAUSED';
          }
        },
        hasNotStarted: {
          get: function() {
            return item.status === 'NOT_STARTED';
          }
        },
        isCanceled: {
          get: function() {
            return item.status === 'CANCELED';
          }
        },
        isSuspended: {
          get: function() {
            return item.status === 'SUSPENDED';
          }
        },
        isPaused: {
          get: function() {
            return item.status === 'PAUSED';
          }
        },
        status: {
          // Returns either SUCCEEDED, RUNNING, FAILED, CANCELED, or NOT_STARTED
          get: function() { return normalizeStatus(item); },
          set: function(status) {
            item.originalStatus = status;
            normalizeStatus(item);
          }
        },
        runningTime: {
          get: function() {
            return momentService
              .duration(parseInt(item.endTime) - parseInt(item.startTime))
              .humanize();
          }
        },
        runningTimeInMs: {
          get: calculateRunningTime(item)
        }
      });
    }

    function addRunningTime(item) {
      if (!item || typeof item !== 'object') {
        return;
      }
      var testDescriptor = Object.getOwnPropertyDescriptor(item, 'runningTime');
      if (testDescriptor && !testDescriptor.enumerable) {
        return;
      }
      Object.defineProperties(item, {
        runningTimeInMs: {
          get: calculateRunningTime(item)
        }
      });
    }

    function normalizeStatus(item) {
      switch(item.originalStatus) {
        case 'SKIPPED':
          return 'SKIPPED';
        case 'COMPLETED':
        case 'SUCCEEDED':
          return 'SUCCEEDED';
        case 'STARTED':
        case 'EXECUTING':
        case 'RUNNING':
          return 'RUNNING';
        case 'FAILED':
        case 'TERMINAL':
          return 'TERMINAL';
        case 'STOPPED':
          return 'STOPPED';
        case 'SUSPENDED':
        case 'DISABLED':
          return 'SUSPENDED';
        case 'NOT_STARTED':
          return 'NOT_STARTED';
        case 'CANCELED':
          return 'CANCELED';
        case 'UNKNOWN':
          return 'UNKNOWN';
        case 'TERMINATED':
          return 'TERMINATED';
        case 'PAUSED':
          return 'PAUSED';
        case 'FAILED_CONTINUE':
          return 'FAILED_CONTINUE';
        default:
          if (item.originalStatus) {
            $log.warn('Unrecognized status:', item.originalStatus);
          }
          return item.originalStatus;
      }
    }

    return {
      defineProperties: defineProperties,
      addRunningTime: addRunningTime,
    };
  });

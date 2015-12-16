'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.orchestratedItem.transformer', [
  require('../utils/moment.js')
])
  .factory('orchestratedItemTransformer', function(momentService, $log) {
    function defineProperties(item) {
      if (!item || typeof item !== 'object') {
        return;
      }
      var testDescriptor = Object.getOwnPropertyDescriptor(item, 'runningTime');
      if (testDescriptor && !testDescriptor.enumerable) {
        return;
      }

      item.originalStatus = item.status;
      Object.defineProperties(item, {
        isCompleted: {
          get: function() {
            return item.status === 'COMPLETED';
          },
        },
        isRunning: {
          get: function() {
            return item.status === 'RUNNING';
          },
        },
        isFailed: {
          get: function() {
            return item.status === 'FAILED';
          },
        },
        isActive: {
          get: function() {
            return item.status === 'RUNNING' || item.status === 'SUSPENDED' || item.status === 'NOT_STARTED';
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
        status: {
          // Returns either COMPLETED, RUNNING, FAILED, CANCELED, or NOT_STARTED
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
          get: function() {
            return (parseInt(item.endTime) || new Date().getTime()) - parseInt(item.startTime);
          }
        }
      });
    }

    function normalizeStatus(item) {
      switch(item.originalStatus) {
        case 'COMPLETED':
        case 'SUCCEEDED':
          return 'COMPLETED';
        case 'STARTED':
        case 'EXECUTING':
        case 'RUNNING':
          return 'RUNNING';
        case 'FAILED':
        case 'TERMINAL':
          return 'FAILED';
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
        default:
          if (item.originalStatus) {
            $log.warn('Unrecognized status:', item.originalStatus);
          }
          return item.originalStatus;
      }
    }

    return {
      defineProperties: defineProperties,
    };
  });

'use strict';

angular.module('deckApp')
  .factory('orchestratedItem', function(momentService) {
    function defineProperties(item) {
      Object.defineProperties(item, {
        isCompleted: {
          get: function() {
            return item.status === 'COMPLETED' || item.status === 'SUCCEEDED';
          },
        },
        isRunning: {
          get: function() {
            return item.status === 'STARTED' || item.status === 'EXECUTING' || item.status === 'RUNNING';
          },
        },
        isFailed: {
          get: function() {
            return item.status === 'FAILED' || item.status === 'STOPPED' || item.status === 'TERMINAL';
          },
        },
        isStopped: {
          get: function() {
            return item.status === 'STOPPED';
          }
        },
        hasNotStarted: {
          get: function() {
            return item.status === 'NOT_STARTED';
          }
        },
        normalizedStatus: {
          get: function() {
            switch(item.status) {
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
              default:
                return item.status;
            }
          }
        },
        runningTime: {
          get: function() {
            return momentService
              .duration(parseInt(item.endTime) - parseInt(item.startTime))
              .humanize();
          }
        }
      });
    }

    return {
      defineProperties: defineProperties,
    };
  });

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

'use strict';

var angular = require('angular');

angular.module('deckApp')
  .factory('taskTracker', function(notifications, scheduler) {
    var getValueForKey = function(task, k) {
      return task.variables.filter(function(v) {
        return v.key === k;
      })[0].value; // assume only one
    };

    return {
      getCompleted: function(oldTasks, newTasks) {
        return newTasks.filter(function(task) {
          return task.status === 'COMPLETED';
        }).filter(function(task) {
          var hasNotBeenSeen = oldTasks.every(function(oldTask) {
            return oldTask.id !== task.id;
          });
          var hasBeenSeenIncomplete = oldTasks.some(function(oldTask) {
            return oldTask.id === task.id && oldTask.status === 'STARTED';
          });
          return hasNotBeenSeen || hasBeenSeenIncomplete;
        });
      },
      handleCompletedTasks: function(tasks) {
        if (tasks.some(function(task) {
          return task.steps.some(function(step) {
            return step.name === 'ForceCacheRefreshStep';
          });
        })) {
          scheduler.scheduleImmediate();
        }
        tasks.forEach(function(task) {
          // generate notifications
          notifications.create({
            title: getValueForKey(task, 'application'),
            message: getValueForKey(task, 'description') + ' Completed successfully',
            href: '/',
          });
        });
      },
    };
  });

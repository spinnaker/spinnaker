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
        return oldTasks.filter(function(task) {
          // filter out completed tasks
          return task.status !== 'COMPLETED';
        }).reduce(function(acc, oldTask) {
          // find tasks that have since completed
          var completed = newTasks.filter(function(newTask) {
            var hasNotBeenSeenAndIsComplete = oldTasks.every(function(oldTask) {
              return oldTask.id !== newTask.id;
            }) && newTask.status === 'COMPLETED';
            var hasBeenSeenAndIsComplete = newTask.id && newTask.status === 'COMPLETED';
            return hasNotBeenSeenAndIsComplete || hasBeenSeenAndIsComplete;
          });
          if (completed.length > 0) {
            // assume there is only one
            acc.push(completed[0]);
          }
          return acc;
        },[]);
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

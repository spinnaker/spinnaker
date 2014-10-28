'use strict';


angular.module('deckApp')
  .factory('taskTracker', function(notifications, scheduler) {
    var that = {};
    that.getValueForKey = function getValueForKey(task, k) {
      var variables =  task.variables.filter(function(v) {
        return v.key === k;
      });
      return variables.length ? variables[0].value : null; // assume only one
    };

    that.getTasksMatchingPred = function getTasksMatchingPred(oldTasks, newTasks, pred) {
      return newTasks.filter(pred)
        .filter(function(task) {
          var hasNotBeenSeen = oldTasks.every(function(oldTask) {
            return oldTask.id !== task.id;
          });
          var hasBeenSeenIncomplete = oldTasks.some(function(oldTask) {
            return oldTask.id === task.id && oldTask.status === 'STARTED';
          });
          return hasNotBeenSeen || hasBeenSeenIncomplete;
        });
    };

    that.getCompleted = function getCompleted(oldTasks, newTasks) {
      return that.getTasksMatchingPred(oldTasks, newTasks, function(task) {
        return task.status === 'COMPLETED';
      });
    };

    that.getFailed = function getFailed(oldTasks, newTasks) {
      return that.getTasksMatchingPred(oldTasks, newTasks, function(task) {
        return task.status === 'FAILED' || task.status === 'STOPPED';
      });
    };

    that.forceRefreshFromTasks = function forceRefreshFromTasks(tasks) {
      if (tasks.some(function(task) {
        return task.steps.some(function(step) {
          return step.name === 'ForceCacheRefreshStep';
        });
      })) {
        scheduler.scheduleImmediate();
        return true;
      }
      return false;
    };

    that.generateNotifications = function generateNotifications(tasks, appendedMessage) {
      tasks.forEach(function(task) {
        // generate notifications
        notifications.create({
          title: that.getValueForKey(task, 'application') || '(unknown)',
          message: (that.getValueForKey(task, 'description') || '') + ' ' + appendedMessage,
          href: '/',
        });
      });
    };

    that.handleTaskUpdates = function handleTaskUpdates(oldTasks, newTasks) {
      var completed = that.getCompleted(oldTasks, newTasks);
      if (completed.length > 0) {
        that.forceRefreshFromTasks(completed);
        that.generateNotifications(completed, 'Completed Successfully');
      }

      var failed = that.getFailed(oldTasks, newTasks);
      if (failed.length > 0) {
        that.generateNotifications(failed, 'Failed');
      }
    };

    return that;

  });

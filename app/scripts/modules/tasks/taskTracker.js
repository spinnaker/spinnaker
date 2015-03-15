'use strict';


angular.module('deckApp.tasks.tracker', [
  'deckApp.scheduler',
  'deckApp.notifications.service'
])
  .factory('taskTracker', function(notificationsService, scheduler) {
    var that = {};
    that.getValueForKey = function getValueForKey(task, k) {
      if (!task.variables) {
        return null;
      }
      var variables =  task.variables.filter(function(v) {
        return v.key === k;
      });
      return variables.length ? variables[0].value : null;
    };

    that.getTasksMatchingPred = function getTasksMatchingPred(oldTasks, newTasks, pred) {
      oldTasks = oldTasks || [];
      newTasks = newTasks || [];
      return newTasks.filter(pred)
        .filter(function(task) {
          var hasNotBeenSeen = oldTasks.every(function(oldTask) {
            return oldTask.id !== task.id;
          });
          var hasBeenSeenIncomplete = oldTasks.some(function(oldTask) {
            return oldTask.id === task.id && oldTask.status === 'RUNNING';
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
        return task.status === 'FAILED';
      });
    };

    that.forceRefreshFromTasks = function forceRefreshFromTasks(tasks) {
      if (tasks.some(function(task) {
        return task.steps && task.steps.some(function(step) {
          return step.name === 'forceCacheRefresh';
        });
      })) {
        scheduler.scheduleImmediate();
        return true;
      }
      return false;
    };

    that.getApplicationNameFromTask = function(task) {
      return that.getValueForKey(task, 'application') || task.application;
    };

    that.getDescriptionFromTask = function(task) {
      return that.getValueForKey(task, 'description') || task.name;
    };

    that.generateNotifications = function generateNotifications(tasks, appendedMessage) {
      tasks.forEach(function(task) {
        // generate notifications
        notificationsService.create({
          title: that.getApplicationNameFromTask(task),
          message: that.getDescriptionFromTask(task) + ' ' + appendedMessage
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

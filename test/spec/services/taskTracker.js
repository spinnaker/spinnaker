'use strict';

describe('Service: taskTracker', function() {
  beforeEach(function() {
    var notifications = {
      create: angular.noop,
    };

    var scheduler = {
      scheduleImmediate: angular.noop,
    };

    this.initialSnapshot = TasksFixture.initialSnapshot;
    this.secondSnapshot = TasksFixture.secondSnapshot;

    spyOn(notifications, 'create');
    spyOn(scheduler, 'scheduleImmediate');

    module('deckApp');

    module(function($provide) {
      $provide.value('notifications', notifications);
      $provide.value('scheduler', scheduler);
    });

    this.notifications = notifications;
    this.scheduler = scheduler;
  });

  beforeEach(inject(function(taskTracker) {
    this.taskTracker = taskTracker;
  }));

  describe('getCompleted(old, new)', function() {
    it('returns a list of tasks that were not completed in old, but are in new', function() {
      var result = this.taskTracker.getCompleted(this.initialSnapshot, this.secondSnapshot);
      expect(result[0]).toBe(this.secondSnapshot[0]);
    });

    it('returns completed tasks in new even if they were not seen in old', function() {
      expect(this.initialSnapshot.every(function(task) {
        return task.id !== 5;
      })).toBe(true);

      expect(this.secondSnapshot.some(function(task) {
        return task.id === 5 && task.status === 'COMPLETED';
      })).toBe(true);

      var completed = this.taskTracker.getCompleted(this.initialSnapshot, this.secondSnapshot);

      expect(completed.some(function(task) {
        return task.id === 5 && task.status === 'COMPLETED';
      })).toBe(true);
    });
  });

  describe('forceRefreshFromTasks(tasks)', function() {
    it('checks for a task that has a ForceCacheRefreshStep', function() {
      function checkForForceCacheRefresh(tasks) {
        return tasks.some(function(task) {
          return task.steps.some(function(step) {
            return step.name === 'ForceCacheRefreshStep';
          });
        });
      }
      expect(checkForForceCacheRefresh(this.initialSnapshot)).toBe(false);
      expect(checkForForceCacheRefresh(this.secondSnapshot)).toBe(true);

      expect(this.taskTracker.forceRefreshFromTasks(this.initialSnapshot)).toBe(false);
      expect(this.taskTracker.forceRefreshFromTasks(this.secondSnapshot)).toBe(true);

    });

    it('calls scheduler.scheduleImmediate when a ForceCacheRefreshStep is found', function() {
      this.taskTracker.forceRefreshFromTasks(this.initialSnapshot);
      expect(this.scheduler.scheduleImmediate).not.toHaveBeenCalled();

      this.taskTracker.forceRefreshFromTasks(this.secondSnapshot);
      expect(this.scheduler.scheduleImmediate).toHaveBeenCalled();
    });
  });

  describe('generateNotifications(tasks, appendedMessage)', function() {
    it('generates one notification for each task', function() {
      this.taskTracker.generateNotifications(this.secondSnapshot);
      expect(this.notifications.create.calls.length).toEqual(this.secondSnapshot.length);
    });
  });

  describe('handleTaskUpdates', function() {
    beforeEach(function() {
      spyOn(this.taskTracker, 'generateNotifications');
    });

    it('will generate a success notification for each completed task', function() {
      spyOn(this.taskTracker, 'getCompleted').andReturn(this.secondSnapshot);
      this.taskTracker.handleTaskUpdates(this.initialSnapshot, this.secondSnapshot);
      expect(this.taskTracker.generateNotifications)
        .toHaveBeenCalledWith(this.secondSnapshot, 'Completed Successfully');
    });

    it('will generate a failure notification for each failed task', function() {
      spyOn(this.taskTracker, 'getFailed').andReturn(this.secondSnapshot);
      this.taskTracker.handleTaskUpdates(this.initialSnapshot, this.secondSnapshot);
      expect(this.taskTracker.generateNotifications)
        .toHaveBeenCalledWith(this.secondSnapshot, 'Failed');
    });

    it('will initiate a force refresh when there are completed tasks', function() {
      spyOn(this.taskTracker, 'forceRefreshFromTasks');
      spyOn(this.taskTracker, 'getCompleted').andReturn(this.secondSnapshot);
      this.taskTracker.handleTaskUpdates(this.initialSnapshot, this.secondSnapshot);
      expect(this.taskTracker.forceRefreshFromTasks).toHaveBeenCalled();
    });

    it('will not initiate a force refresh when there are no completed tasks', function() {
      spyOn(this.taskTracker, 'forceRefreshFromTasks');
      spyOn(this.taskTracker, 'getCompleted').andReturn([]);
      this.taskTracker.handleTaskUpdates(this.initialSnapshot, this.initialSnapshot);
      expect(this.taskTracker.forceRefreshFromTasks).not.toHaveBeenCalled();
    });
  });
});

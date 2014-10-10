'use strict';

describe('Filter: taskFilter', function() {
  beforeEach(function() {
    this.tasks = TasksFixture.secondSnapshot;
    module('deckApp');
  });

  beforeEach(inject(function(taskFilterFilter) {
    this.taskFilter = taskFilterFilter;
  }));

  describe('the filtering logic', function() {
    it('treats STOPPED tests like FAILED ones', function() {
      expect(this.tasks.some(function(task) {
        return task.status === 'FAILED';
      })).toBe(true);

      expect(this.tasks.some(function(task) {
        return task.status === 'STOPPED';
      })).toBe(true);

      expect(this.tasks.some(function(task) {
        return task.status !== 'STOPPED' || task.status !== 'FAILED';
      })).toBe(true);

      var filtered = this.taskFilter(this.tasks, 'Errored');

      expect(filtered.some(function(task) {
        return task.status === 'FAILED';
      })).toBe(true);

      expect(filtered.some(function(task) {
        return task.status === 'STOPPED';
      })).toBe(true);

      expect(filtered.every(function(task) {
        return task.status === 'STOPPED' || task.status === 'FAILED';
      })).toBe(true);
    });
  });
});

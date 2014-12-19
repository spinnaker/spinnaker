/* globals TasksFixture */
'use strict';


describe('Filter: taskFilter', function() {
  beforeEach(function() {
    module('deckApp');
  });

  beforeEach(inject(function(taskFilterFilter, orchestratedItem) {
    this.taskFilter = taskFilterFilter;
    this.tasks = TasksFixture.secondSnapshot;
    this.tasks.forEach(orchestratedItem.defineProperties);

  }));


  describe('the filtering logic', function() {
    it('should return all tasks if "All" selected ', function () {
      expect(this.taskFilter(this.tasks, 'All')).toEqual(this.tasks);
    });

    it('should return only "RUNNING" task if "Running" selected', function() {
      var filteredList = (this.taskFilter(this.tasks, 'Running'));
      expect(filteredList.length).toBe(1);
      expect(filteredList[0].status).toEqual('RUNNING');
    });

    it('should return COMPLETED tasks if "Completed" selected', function () {
      var filteredList = (this.taskFilter(this.tasks, 'Completed'));
      expect(filteredList.length).toBe(4);
      expect(filteredList[0].status).toEqual('COMPLETED');
    });

    it('should return FAILED tasks if "Errored" selected', function () {
      var filteredList = (this.taskFilter(this.tasks, 'Errored'));
      expect(filteredList.length).toBe(4);
      filteredList.forEach(function(task) {
        expect(['FAILED']).toContain(task.status);
      });
    });



    it('treats SUSPENDED tests like FAILED ones', function() {
      expect(this.tasks.some(function(task) {
        return task.originalStatus === 'FAILED';
      })).toBe(true);

      expect(this.tasks.some(function(task) {
        return task.originalStatus === 'SUSPENDED';
      })).toBe(true);

      expect(this.tasks.some(function(task) {
        return task.originalStatus !== 'SUSPENDED' || task.originalStatus !== 'FAILED';
      })).toBe(true);

      var filtered = this.taskFilter(this.tasks, 'Errored');

      expect(filtered.some(function(task) {
        return task.originalStatus === 'FAILED';
      })).toBe(true);

      expect(filtered.some(function(task) {
        return task.originalStatus === 'SUSPENDED';
      })).toBe(true);

      expect(filtered.every(function(task) {
        return task.originalStatus === 'SUSPENDED' || task.originalStatus === 'FAILED' || task.originalStatus === 'TERMINAL' || task.originalStatus === 'STOPPED';
      })).toBe(true);
    });
  });
});

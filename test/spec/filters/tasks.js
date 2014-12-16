/* globals TasksFixture */
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
    it('should return all tasks if "All" selected ', function () {
      expect(this.taskFilter(this.tasks, 'All')).toEqual(this.tasks);
    });

    it('should return only "RUNNING" task if "Running" selected', function() {
      var filteredList = (this.taskFilter(this.tasks, 'Running'))
      expect(filteredList.length).toBe(1);
      expect(filteredList[0].status).toEqual('RUNNING');
    });

    it('should return SUCCEEDED tasks if "Completed" selected', function () {
      var filteredList = (this.taskFilter(this.tasks, 'Completed'));
      expect(filteredList.length).toBe(1);
      expect(filteredList[0].status).toEqual('SUCCEEDED');
    });

    it('should return FAILED and TERMINAL tasks if "Errored" selected', function () {
      var filteredList = (this.taskFilter(this.tasks, 'Errored'));
      expect(filteredList.length).toBe(3);
      filteredList.forEach(function(task) {
        expect(['FAILED', 'TERMINAL', 'SUSPENDED']).toContain(task.status);
      });
    });



    it('treats SUSPENDED tests like FAILED ones', function() {
      expect(this.tasks.some(function(task) {
        return task.status === 'FAILED';
      })).toBe(true);

      expect(this.tasks.some(function(task) {
        return task.status === 'SUSPENDED';
      })).toBe(true);

      expect(this.tasks.some(function(task) {
        return task.status !== 'SUSPENDED' || task.status !== 'FAILED';
      })).toBe(true);

      var filtered = this.taskFilter(this.tasks, 'Errored');

      expect(filtered.some(function(task) {
        return task.status === 'FAILED';
      })).toBe(true);

      expect(filtered.some(function(task) {
        return task.status === 'SUSPENDED';
      })).toBe(true);

      expect(filtered.every(function(task) {
        return task.status === 'SUSPENDED' || task.status === 'FAILED' || task.status === 'TERMINAL';
      })).toBe(true);
    });
  });
});

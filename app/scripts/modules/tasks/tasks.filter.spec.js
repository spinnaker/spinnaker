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
      expect(filteredList.length).toBe(3);
      filteredList.forEach(function(task) {
        expect(['FAILED']).toContain(task.status);
      });
    });
  });
});

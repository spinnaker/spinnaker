'use strict';

describe('Controller: tasks', function () {
  var controller;
  var controllerInjector;
  var scope;

  controllerInjector = function (appData) {
    appData.registerAutoRefreshHandler = angular.noop;
    return function ($controller, $rootScope) {
      scope = $rootScope.$new();
      controller = $controller('TasksCtrl', { application: appData, $scope: scope });
    };
  };

  beforeEach(module('deckApp.tasks.main'));

  beforeEach(
    inject(
      controllerInjector({})
    )
  );

  describe('initialization', function() {
    it('tasksLoaded flag should be false', function() {
      scope.$digest();
      expect(controller.tasksLoaded).toBe(false);
    });

    it('tasksLoaded flag should be true if tasks object is present on application', function() {
      inject(controllerInjector({tasks: [] }));
      scope.$digest();
      expect(controller.tasksLoaded).toBe(true);
    });
  });

  describe('Filtering Task list with one running task', function () {
    var application = {
      tasks: [
        {status: 'COMPLETED'},
        {status: 'RUNNING'},
      ]
    };

    beforeEach(
      inject(
        controllerInjector(application)
      )
    );

    it('should sort the tasks with the RUNNING status at the top', function () {
       var sortedList = controller.sortTasks();
      expect(sortedList.length).toBe(2);
      expect(sortedList[0].status).toEqual('RUNNING');
    });
  });

  describe('Filtering Task list by startTime in descending order with only running task', function () {
    var application = {
      tasks: [
        {status: 'RUNNING', startTime:20},
        {status: 'RUNNING', startTime:99},
      ]
    };

    beforeEach(
      inject(
        controllerInjector(application)
      )
    );

    it('should sort the tasks with the RUNNING status at the top', function () {
       var sortedList = controller.sortTasks();
      expect(sortedList.length).toBe(2);
      expect(sortedList[0].startTime).toBe(99);
      sortedList.forEach(function(task) {
        expect(task.status).toEqual('RUNNING');
      });
    });
  });

  describe('Filtering Task list with zero running task', function () {
    var application = {
      tasks: [
        {status: 'COMPLETED', startTime: 22},
        {status: 'COMPLETED', startTime: 100},
      ]
    };

    beforeEach(
      inject(
        controllerInjector(application)
      )
    );

    it('should sort the tasks in descending order by startTime', function () {
      var sortedList = controller.sortTasks();
      expect(sortedList.length).toBe(2);
      expect(sortedList[0].startTime).toBe(100);
      sortedList.forEach(function(task) {
        expect(task.status).toEqual('COMPLETED');
      });
    });
  });


});

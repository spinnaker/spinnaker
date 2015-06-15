'use strict';

describe('Controller: tasks', function () {
  var controller;
  var controllerInjector;
  var scope;

  controllerInjector = function (appData) {
    appData.registerAutoRefreshHandler = angular.noop;
    return function ($controller, $rootScope) {
      var viewStateCache = { createCache: function() { return { get: angular.noop, put: angular.noop }; }};
      scope = $rootScope.$new();
      controller = $controller('TasksCtrl', { application: appData, $scope: scope, viewStateCache: viewStateCache });
    };
  };

  beforeEach(module('spinnaker.tasks.main'));

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
        {status: 'COMPLETED', name: 'a'},
        {status: 'RUNNING', name: 'a'},
      ]
    };

    beforeEach(
      inject(
        controllerInjector(application)
      )
    );

    it('should sort the tasks with the RUNNING status at the top', function () {
      controller.sortTasks();
      expect(controller.sortedTasks.length).toBe(2);
      expect(controller.sortedTasks[0].status).toEqual('RUNNING');
    });
  });

  describe('Filtering Task list by startTime in descending order with only running task', function () {
    var application = {
      tasks: [
        {status: 'RUNNING', startTime:20, name: 'a'},
        {status: 'RUNNING', startTime:99, name: 'a'},
      ]
    };

    beforeEach(
      inject(
        controllerInjector(application)
      )
    );

    it('should sort the tasks with the RUNNING status at the top', function () {
      controller.sortTasks();
      var sortedList = controller.sortedTasks;
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
        {status: 'COMPLETED', startTime: 22, name: 'a'},
        {status: 'COMPLETED', startTime: 100, name: 'a'},
      ]
    };

    beforeEach(
      inject(
        controllerInjector(application)
      )
    );

    it('should sort the tasks in descending order by startTime', function () {
      controller.sortTasks();
      var sortedList = controller.sortedTasks;
      expect(sortedList.length).toBe(2);
      expect(sortedList[0].startTime).toBe(100);
      sortedList.forEach(function(task) {
        expect(task.status).toEqual('COMPLETED');
      });
    });
  });

  describe('get deployed server group:', function () {

    it('should return undefined if the task does not have the "execution" property', function () {
      var task = {};
      var step = {};
      var results = controller.getDeployedServerGroup(task, step);

      expect(results).toBeUndefined();
    });

    it('should return undefined if no stage is found for a step', function () {
      var stepName = 'createCopyLastAsg';
      var task = {
        execution: {
          stages: []
        }
      };

      var step = {name: stepName};

      var results = controller.getDeployedServerGroup(task, step);

      expect(results).toBeUndefined();
    });

    it('should return undefined if no stage context is found for a step', function () {
      var stepName = 'createCopyLastAsg';
      var task = {
        execution: {
          stages: [
            {
              tasks:[
                {name: stepName}
              ]
            }
          ]
        }
      };

      var step = {name: stepName};

      var results = controller.getDeployedServerGroup(task, step);

      expect(results).toBeUndefined();
    });

    it('should return the deployed.server.group for the stage that the step is in: one stage', function () {
      var stepName = 'createCopyLastAsg';
      var task = {
        execution: {
          stages: [
            {
              context: {
                'deploy.server.groups': {
                  'us-west-1': ['mahe-prod-v028']
                }
              },
              tasks: [
                {name: stepName}
              ]
            }
          ]
        }
      };

      var step = {name: stepName};

      var results = controller.getDeployedServerGroup(task, step);

      expect(results).toEqual('mahe-prod-v028');
    });


    it('should return the deployed.server.group for the stage that the step is in: multiple stage', function () {
      var stepName = 'destroyAsg';
      var task = {
        execution: {
          stages: [
            {
              context: {
                'deploy.server.groups': {
                  'us-west-1': ['mahe-prod-v028']
                }
              },
              tasks: [
                {name: 'createCopyLastAsg'}
              ]
            },
            {
              context: {
                'deploy.server.groups': {
                  'us-west-1': ['mahe-prod-v027']
                }
              },
              tasks: [
                {name: 'destroyAsg'}
              ]
            }
          ]
        }
      };

      var step = {name: stepName};

      var results = controller.getDeployedServerGroup(task, step);

      expect(results).toEqual('mahe-prod-v027');

    });



  });

});

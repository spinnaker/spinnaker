'use strict';

describe('Controller: tasks', function () {
  const angular = require('angular');

  var controller;
  var controllerInjector;
  var scope;

  controllerInjector = function (appData) {
    appData.registerAutoRefreshHandler = angular.noop;
    return function ($controller, $rootScope) {
      var viewStateCache = { createCache: function() { return { get: angular.noop, put: angular.noop }; }};
      scope = $rootScope.$new();
      controller = $controller('TasksCtrl', { app: appData, $scope: scope, viewStateCache: viewStateCache });
    };
  };

  beforeEach(
    window.module(
      require('./tasks.controller.js')
    )
  );

  beforeEach(
    window.inject(
      controllerInjector({})
    )
  );

  describe('initialization', function() {
    it('tasksLoaded flag should be false', function() {
      scope.$digest();
      expect(controller.tasksLoaded).toBe(false);
    });

    it('tasksLoaded flag should be true if tasks object is present on application', function() {
      window.inject(controllerInjector({tasks: [] }));
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
      window.inject(
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
      window.inject(
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
      window.inject(
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

  describe('get first deployed server group:', function() {

    it('should return undefined if the task does not have any execution property', function () {
      var task = {};

      var result = controller.getFirstDeployServerGroupName(task);
      expect(result).toBeUndefined();
    });

    it('should return undefined if there is a stage with ZERO deploy.server.groups in the context', function () {
      var task = {
        execution: {
          stages: [
            {
              tasks:[
                {name: 'createCopyLastAsg'}
              ]
            }
          ]
        }
      };

      var result = controller.getFirstDeployServerGroupName(task);
      expect(result).toBeUndefined();
    });

    it('should return the first deploy.server.group value from context on clone operations', function () {
      var task = {
        execution: {
          stages: [
            {
              context: {
                'deploy.server.groups': {
                  'us-west-1': ['mahe-prod-v028']
                }
              },
              tasks:[
                {name: 'createCopyLastAsg'}
              ]
            }
          ]
        }
      };

      var result = controller.getFirstDeployServerGroupName(task);

      expect(result).toBe('mahe-prod-v028');
    });

    it('should return the first deploy.server.group value from context on fresh deploys', function () {
      var task = {
        execution: {
          stages: [
            {
              context: {
                'deploy.server.groups': {
                  'us-west-1': ['mahe-prod-v021']
                }
              },
              tasks:[
                {name: 'createDeploy'}
              ]
            }
          ]
        }
      };

      var result = controller.getFirstDeployServerGroupName(task);

      expect(result).toBe('mahe-prod-v021');
    });

    it('should return the first deploy.server.group value from context if there are multiple', function () {
      var task = {
        execution: {
          stages: [
            {
              context: {
                'deploy.server.groups': {
                  'us-west-1': ['mahe-prod-v028']
                }
              },

              tasks:[
                {name: 'createCopyLastAsg'}
              ]
            },
            {
              context: {
                'deploy.server.groups': {
                  'us-west-1': ['mahe-prod-v027']
                }
              },
              tasks:[
                {name: 'createCopyLastAsg'}
              ]
            }
          ]
        }
      };

      var result = controller.getFirstDeployServerGroupName(task);

      expect(result).toBe('mahe-prod-v028');
    });
  });

});

import modelBuilderModule from '../application/applicationModel.builder';

describe('Controller: tasks', function () {
  var controller;
  var taskWriter;
  var scope;
  var $q;

  beforeEach(
    window.module(
      require('./tasks.controller.js'),
      modelBuilderModule
    )
  );

  beforeEach(
    window.inject(function($controller, $rootScope, _$q_, _taskWriter_, applicationModelBuilder) {
      $q = _$q_;
      taskWriter = _taskWriter_;

      this.initializeController = (tasks) => {
        let application = applicationModelBuilder.createApplication({key: 'tasks', lazy: true});
        application.tasks.activate = angular.noop;
        application.tasks.data = tasks || [];
        application.tasks.loaded = true;
        application.tasks.dataUpdated();

        let confirmationModalService = {
          confirm: function(params) {
            $q.when(null).then(params.submitMethod);
          }
        };
        var viewStateCache = { createCache: function() { return { get: angular.noop, put: angular.noop }; }};
        scope = $rootScope.$new();
        controller = $controller('TasksCtrl', {
          app: application,
          $scope: scope,
          viewStateCache: viewStateCache,
          confirmationModalService: confirmationModalService,
          taskWriter: taskWriter,
        });
      };
    })
  );

  describe('initialization', function() {
    beforeEach(function() { this.initializeController(); });

    it('loading flag should be true', function() {
      expect(scope.viewState.loading).toBe(true);
    });

    it('loading flag should be false when tasks reloaded', function() {
      scope.$digest();
      expect(scope.viewState.loading).toBe(false);
    });
  });

  describe('task reloading', function () {
    it ('should sort tasks whenever a tasksReloaded event occurs', function () {
      this.initializeController();
      scope.$digest();
      expect(controller.sortedTasks.length).toBe(0);

      controller.application.tasks.data.push({isActive: true, startTime:20, name: 'a'});
      controller.application.tasks.dataUpdated();
      scope.$digest();

      expect(controller.sortedTasks.length).toBe(1);
    });
  });

  describe('deleting tasks', function () {
    it ('should confirm delete, then perform delete, then reload tasks', function () {
      var taskReloadCalls = 0,
          tasks = [ {id: 'a', name: 'resize something'} ];
      spyOn(taskWriter, 'deleteTask').and.returnValue($q.when(null));

      this.initializeController(tasks);
      spyOn(controller.application.tasks, 'refresh').and.callFake(() => taskReloadCalls++);
      scope.$digest();

      expect(taskReloadCalls).toBe(0);
      expect(taskWriter.deleteTask.calls.count()).toBe(0);

      controller.deleteTask('a');

      scope.$digest();
      expect(taskWriter.deleteTask.calls.count()).toBe(1);
      expect(taskReloadCalls).toBe(1);
    });
  });

  describe('canceling tasks', function () {
    it ('should confirm delete, then perform delete, then reload tasks', function () {
      var taskReloadCalls = 0,
          tasks = [ {id: 'a', name: 'resize something'} ];
      spyOn(taskWriter, 'cancelTask').and.returnValue($q.when(null));

      this.initializeController(tasks);
      spyOn(controller.application.tasks, 'refresh').and.callFake(() => taskReloadCalls++);
      scope.$digest();

      expect(taskReloadCalls).toBe(0);
      expect(taskWriter.cancelTask.calls.count()).toBe(0);

      controller.cancelTask('a');

      scope.$digest();
      expect(taskWriter.cancelTask.calls.count()).toBe(1);
      expect(taskReloadCalls).toBe(1);
    });
  });

  describe('Filtering Task list with one running task', function () {
    var tasks = [
        {isActive: false, name: 'a'},
        {isActive: true, name: 'a'},
      ];

    it('should sort the tasks with the RUNNING status at the top', function () {
      this.initializeController(tasks);
      controller.sortTasks();
      expect(controller.sortedTasks.length).toBe(2);
      expect(controller.sortedTasks[0].isActive).toBe(true);
    });
  });

  describe('Filtering Task list by startTime in descending order with only running task', function () {
    var tasks = [
        {isActive: true, startTime:20, name: 'a'},
        {isActive: true, startTime:99, name: 'a'},
      ];

    it('should sort the tasks with the RUNNING status at the top', function () {
      this.initializeController(tasks);
      controller.sortTasks();
      var sortedList = controller.sortedTasks;
      expect(sortedList.length).toBe(2);
      expect(sortedList[0].startTime).toBe(99);
      sortedList.forEach(function(task) {
        expect(task.isActive).toBe(true);
      });
    });
  });

  describe('Filtering Task list with zero running task', function () {
    var tasks = [
        {isActive: false, startTime: 22, name: 'a'},
        {isActive: false, startTime: 100, name: 'a'},
      ];

    it('should sort the tasks in descending order by startTime', function () {
      this.initializeController(tasks);
      controller.sortTasks();
      var sortedList = controller.sortedTasks;
      expect(sortedList.length).toBe(2);
      expect(sortedList[0].startTime).toBe(100);
      sortedList.forEach(function(task) {
        expect(task.isActive).toBe(false);
      });
    });
  });

  describe('get first deployed server group:', function() {

    beforeEach(function() { this.initializeController(); });

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

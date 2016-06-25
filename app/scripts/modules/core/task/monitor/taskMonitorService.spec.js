'use strict';

describe('Service: taskMonitorService', function () {

  var taskMonitorService,
      $q,
      $scope,
      $timeout,
      $http,
      orchestratedItemTransformer;

  beforeEach(
    window.module('spinnaker.tasks.monitor.service')
  );

  beforeEach(
    window.inject(function (_taskMonitorService_, _$q_, $rootScope, _$timeout_, $httpBackend, _orchestratedItemTransformer_) {
      taskMonitorService = _taskMonitorService_;
      $q = _$q_;
      $scope = $rootScope.$new();
      $timeout = _$timeout_;
      $http = $httpBackend;
      orchestratedItemTransformer = _orchestratedItemTransformer_;
    })
  );

  describe('task submit', function () {
    it('waits for task to complete, then calls onComplete', function () {
      let completeCalled = false;
      let task = { id: 'a', status: 'RUNNING' };
      orchestratedItemTransformer.defineProperties(task);

      let operation = () => $q.when(task);
      let monitor = taskMonitorService.buildTaskMonitor({
        onTaskComplete: () => completeCalled = true,
        modalInstance: { result: $q.defer().promise },
        application: { name: 'deck', runningOrchestrations: { refresh: angular.noop } },
      });
      spyOn(monitor.application.runningOrchestrations, 'refresh');

      monitor.submit(operation);

      expect(monitor.submitting).toBe(true);
      expect(monitor.error).toBe(false);

      $timeout.flush(); // still running first time

      $http.expectGET(['/applications', 'deck', 'tasks', 'a'].join('/')).respond(200, { status: 'RUNNING' });
      $timeout.flush();
      $http.flush();
      expect(monitor.task.isCompleted).toBe(false);
      expect(monitor.application.runningOrchestrations.refresh.calls.count()).toBe(1);

      $http.expectGET(['/applications', 'deck', 'tasks', 'a'].join('/')).respond(200, { status: 'SUCCEEDED' });
      $timeout.flush(); // complete second time
      $http.flush();

      expect(completeCalled).toBe(true);
      expect(monitor.task.isCompleted).toBe(true);
    });

    it('sets error when task fails immediately', function () {
      let completeCalled = false;
      let task = { failureMessage: 'it failed' };
      let operation = () => $q.reject(task);
      let monitor = taskMonitorService.buildTaskMonitor({
        onTaskComplete: () => completeCalled = true,
        modalInstance: { result: $q.defer().promise },
        application: { name: 'deck' },
      });

      monitor.submit(operation);

      expect(monitor.submitting).toBe(true);

      $scope.$digest();
      expect(monitor.submitting).toBe(false);
      expect(monitor.error).toBe(true);
      expect(monitor.errorMessage).toBe('it failed');
      expect(completeCalled).toBe(false);
    });

    it('sets error when task fails while polling', function () {
      let completeCalled = false;
      let task = { id: 'a', status: 'RUNNING' };
      orchestratedItemTransformer.defineProperties(task);

      let operation = () => $q.when(task);
      let monitor = taskMonitorService.buildTaskMonitor({
        onTaskComplete: () => completeCalled = true,
        modalInstance: { result: $q.defer().promise },
        application: { name: 'deck', runningOrchestrations: { refresh: angular.noop } }
      });

      monitor.submit(operation);

      expect(monitor.submitting).toBe(true);
      expect(monitor.error).toBe(false);

      $timeout.flush(); // still running first time

      $http.expectGET(['/applications', 'deck', 'tasks', 'a'].join('/')).respond(200, { status: 'RUNNING' });
      $timeout.flush();
      $http.flush();
      expect(monitor.task.isCompleted).toBe(false);

      $http.expectGET(['/applications', 'deck', 'tasks', 'a'].join('/')).respond(200, { status: 'TERMINAL' });
      $timeout.flush(); // complete second time
      $http.flush();

      expect(monitor.submitting).toBe(false);
      expect(monitor.error).toBe(true);
      expect(monitor.errorMessage).toBe('There was an unknown server error.');
      expect(completeCalled).toBe(false);
    });

  });
});


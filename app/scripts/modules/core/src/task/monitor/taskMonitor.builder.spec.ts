import { mock } from 'angular';
import { IModalServiceInstance } from 'angular-ui-bootstrap';
import { $q, $timeout } from 'ngimport';
import Spy = jasmine.Spy;

import { API_SERVICE, Api } from 'core/api/api.service';
import { ITask } from 'core/domain';
import { TASK_MONITOR_BUILDER, TaskMonitorBuilder } from 'core/task/monitor/taskMonitor.builder';
import { OrchestratedItemTransformer } from 'core/orchestratedItem/orchestratedItem.transformer';
import { APPLICATION_MODEL_BUILDER, ApplicationModelBuilder } from 'core/application/applicationModel.builder';

describe('Service: taskMonitorBuilder', () => {
  let taskMonitorBuilder: TaskMonitorBuilder,
    $scope: ng.IScope,
    $http: ng.IHttpBackendService,
    API: Api,
    applicationModelBuilder: ApplicationModelBuilder;

  beforeEach(mock.module(TASK_MONITOR_BUILDER, API_SERVICE, APPLICATION_MODEL_BUILDER));

  beforeEach(
    mock.inject(
      (
        _taskMonitorBuilder_: TaskMonitorBuilder,
        $rootScope: ng.IRootScopeService,
        $httpBackend: ng.IHttpBackendService,
        _applicationModelBuilder_: ApplicationModelBuilder,
        _API_: Api,
      ) => {
        taskMonitorBuilder = _taskMonitorBuilder_;
        $scope = $rootScope.$new();
        $http = $httpBackend;
        applicationModelBuilder = _applicationModelBuilder_;
        API = _API_;
      },
    ),
  );

  describe('task submit', () => {
    it('waits for task to complete, then calls onComplete', () => {
      let completeCalled = false;
      const task: any = { id: 'a', status: 'RUNNING' };
      OrchestratedItemTransformer.defineProperties(task);

      const operation = () => $q.when(task);
      const monitor = taskMonitorBuilder.buildTaskMonitor({
        application: applicationModelBuilder.createApplication('app', { key: 'runningTasks', lazy: true }),
        title: 'some task',
        modalInstance: { result: $q.defer().promise } as IModalServiceInstance,
        onTaskComplete: () => (completeCalled = true),
      });
      spyOn(monitor.application.getDataSource('runningTasks'), 'refresh');

      monitor.submit(operation);

      expect(monitor.submitting).toBe(true);
      expect(monitor.error).toBe(false);

      $timeout.flush(); // still running first time

      $http.expectGET([API.baseUrl, 'tasks', 'a'].join('/')).respond(200, { status: 'RUNNING' });
      $timeout.flush();
      $http.flush();
      expect(monitor.task.isCompleted).toBe(false);
      expect((monitor.application.getDataSource('runningTasks').refresh as Spy).calls.count()).toBe(1);

      $http.expectGET([API.baseUrl, 'tasks', 'a'].join('/')).respond(200, { status: 'SUCCEEDED' });
      $timeout.flush(); // complete second time
      $http.flush();

      expect(completeCalled).toBe(true);
      expect(monitor.task.isCompleted).toBe(true);
    });

    it('sets error when task fails immediately', () => {
      let completeCalled = false;
      const task = { failureMessage: 'it failed' };
      const operation = () => $q.reject(task);
      const monitor = taskMonitorBuilder.buildTaskMonitor({
        application: applicationModelBuilder.createApplication('app', { key: 'runningTasks', lazy: true }),
        title: 'a task',
        modalInstance: { result: $q.defer().promise } as IModalServiceInstance,
        onTaskComplete: () => (completeCalled = true),
      });

      monitor.submit(operation);

      expect(monitor.submitting).toBe(true);

      $scope.$digest();
      expect(monitor.submitting).toBe(false);
      expect(monitor.error).toBe(true);
      expect(monitor.errorMessage).toBe('it failed');
      expect(completeCalled).toBe(false);
    });

    it('sets error when task fails while polling', () => {
      let completeCalled = false;
      const task = { id: 'a', status: 'RUNNING' } as ITask;
      OrchestratedItemTransformer.defineProperties(task);

      const operation = () => $q.when(task);
      const monitor = taskMonitorBuilder.buildTaskMonitor({
        application: applicationModelBuilder.createApplication('app', { key: 'runningTasks', lazy: true }),
        title: 'a task',
        modalInstance: { result: $q.defer().promise } as IModalServiceInstance,
        onTaskComplete: () => (completeCalled = true),
      });

      monitor.submit(operation);

      expect(monitor.submitting).toBe(true);
      expect(monitor.error).toBe(false);

      $timeout.flush(); // still running first time

      $http.expectGET([API.baseUrl, 'tasks', 'a'].join('/')).respond(200, { status: 'RUNNING' });
      $timeout.flush();
      $http.flush();
      expect(monitor.task.isCompleted).toBe(false);

      $http.expectGET([API.baseUrl, 'tasks', 'a'].join('/')).respond(200, { status: 'TERMINAL' });
      $timeout.flush(); // complete second time
      $http.flush();

      expect(monitor.submitting).toBe(false);
      expect(monitor.error).toBe(true);
      expect(monitor.errorMessage).toBe('There was an unknown server error.');
      expect(completeCalled).toBe(false);
    });
  });
});

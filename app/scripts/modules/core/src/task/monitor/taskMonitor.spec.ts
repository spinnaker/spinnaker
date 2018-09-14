import { mock } from 'angular';
import { IModalServiceInstance } from 'angular-ui-bootstrap';
import { $q, $timeout } from 'ngimport';
import Spy = jasmine.Spy;

import { API } from 'core/api/ApiService';
import { ITask } from 'core/domain';
import { TaskMonitor } from './TaskMonitor';
import { OrchestratedItemTransformer } from 'core/orchestratedItem/orchestratedItem.transformer';
import { APPLICATION_MODEL_BUILDER, ApplicationModelBuilder } from 'core/application/applicationModel.builder';

describe('TaskMonitor', () => {
  let $scope: ng.IScope, $http: ng.IHttpBackendService, applicationModelBuilder: ApplicationModelBuilder;

  beforeEach(mock.module(APPLICATION_MODEL_BUILDER));

  beforeEach(
    mock.inject(
      (
        $rootScope: ng.IRootScopeService,
        $httpBackend: ng.IHttpBackendService,
        _applicationModelBuilder_: ApplicationModelBuilder,
      ) => {
        $scope = $rootScope.$new();
        $http = $httpBackend;
        applicationModelBuilder = _applicationModelBuilder_;
      },
    ),
  );

  describe('task submit', () => {
    it('waits for task to complete, then calls onComplete', () => {
      let completeCalled = false;
      const task: any = { id: 'a', status: 'RUNNING' };
      OrchestratedItemTransformer.defineProperties(task);

      const operation = () => $q.when(task);
      const monitor = new TaskMonitor({
        application: applicationModelBuilder.createApplicationForTests('app', { key: 'runningTasks', lazy: true }),
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
      const monitor = new TaskMonitor({
        application: applicationModelBuilder.createApplicationForTests('app', { key: 'runningTasks', lazy: true }),
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
      const monitor = new TaskMonitor({
        application: applicationModelBuilder.createApplicationForTests('app', { key: 'runningTasks', lazy: true }),
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

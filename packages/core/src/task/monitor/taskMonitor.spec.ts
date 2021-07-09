import { mockHttpClient } from '../../api/mock/jasmine';
import { mock } from 'angular';
import { IModalServiceInstance } from 'angular-ui-bootstrap';
import { $q, $timeout } from 'ngimport';
import Spy = jasmine.Spy;

import { ITask } from '../../domain';
import { TaskMonitor } from './TaskMonitor';
import { OrchestratedItemTransformer } from '../../orchestratedItem/orchestratedItem.transformer';
import { ApplicationModelBuilder } from '../../application/applicationModel.builder';

describe('TaskMonitor', () => {
  let $scope: ng.IScope;

  beforeEach(
    mock.inject(($rootScope: ng.IRootScopeService) => {
      $scope = $rootScope.$new();
    }),
  );

  describe('task submit', () => {
    it('waits for task to complete, then calls onComplete', async () => {
      const http = mockHttpClient();
      let completeCalled = false;
      const task: any = { id: 'a', status: 'RUNNING' };
      OrchestratedItemTransformer.defineProperties(task);

      const operation = () => $q.when(task);
      const monitor = new TaskMonitor({
        application: ApplicationModelBuilder.createApplicationForTests('app', {
          key: 'runningTasks',
          lazy: true,
          defaultData: [],
        }),
        title: 'some task',
        modalInstance: { result: $q.defer().promise } as IModalServiceInstance,
        onTaskComplete: () => (completeCalled = true),
      });
      spyOn(monitor.application.getDataSource('runningTasks'), 'refresh');

      monitor.submit(operation);

      expect(monitor.submitting).toBe(true);
      expect(monitor.error).toBe(false);

      $timeout.flush(); // still running first time

      http.expectGET('/tasks/a').respond(200, { status: 'RUNNING' });
      $timeout.flush();
      await http.flush();
      expect(monitor.task.isCompleted).toBe(false);
      expect((monitor.application.getDataSource('runningTasks').refresh as Spy).calls.count()).toBe(1);

      http.expectGET('/tasks/a').respond(200, { status: 'SUCCEEDED' });
      $timeout.flush(); // complete second time
      await http.flush();

      expect(completeCalled).toBe(true);
      expect(monitor.task.isCompleted).toBe(true);
    });

    it('sets error when task fails immediately', () => {
      let completeCalled = false;
      const task = { failureMessage: 'it failed' };
      const operation = () => $q.reject(task);
      const monitor = new TaskMonitor({
        application: ApplicationModelBuilder.createApplicationForTests('app', {
          key: 'runningTasks',
          lazy: true,
          defaultData: [],
        }),
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

    it('sets error when task fails while polling', async () => {
      const http = mockHttpClient();
      let completeCalled = false;
      const task = { id: 'a', status: 'RUNNING' } as ITask;
      OrchestratedItemTransformer.defineProperties(task);

      const operation = () => $q.when(task);
      const monitor = new TaskMonitor({
        application: ApplicationModelBuilder.createApplicationForTests('app', {
          key: 'runningTasks',
          lazy: true,
          defaultData: [],
        }),
        title: 'a task',
        modalInstance: { result: $q.defer().promise } as IModalServiceInstance,
        onTaskComplete: () => (completeCalled = true),
      });

      monitor.submit(operation);

      expect(monitor.submitting).toBe(true);
      expect(monitor.error).toBe(false);

      $timeout.flush(); // still running first time

      http.expectGET('/tasks/a').respond(200, { status: 'RUNNING' });
      $timeout.flush();
      await http.flush();
      expect(monitor.task.isCompleted).toBe(false);

      http.expectGET('/tasks/a').respond(200, { status: 'TERMINAL' });
      $timeout.flush(); // complete second time
      await http.flush();

      expect(monitor.submitting).toBe(false);
      expect(monitor.error).toBe(true);
      expect(monitor.errorMessage).toBe('There was an unknown server error.');
      expect(completeCalled).toBe(false);
    });
  });
});

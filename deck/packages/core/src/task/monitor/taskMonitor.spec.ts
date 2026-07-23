import type { IModalServiceInstance } from 'angular-ui-bootstrap';

import { TaskMonitor } from './TaskMonitor';
import { ApplicationModelBuilder } from '../../application/applicationModel.builder';
import { mockHttpClient } from '../../api/mock/jasmine';
import type { ITask } from '../../domain';
import { OrchestratedItemTransformer } from '../../orchestratedItem/orchestratedItem.transformer';
import { TaskReader } from '../task.read.service';
import { createDeferred } from '../../utils/deferred';

import Spy = jasmine.Spy;

describe('TaskMonitor', () => {
  const settleNativePromises = async () => {
    await Promise.resolve();
    await Promise.resolve();
  };

  describe('task submit', () => {
    it('waits for task to complete, then calls onComplete', async () => {
      let completeCalled = false;
      const task: any = { id: 'a', status: 'RUNNING' };
      OrchestratedItemTransformer.defineProperties(task);
      const completion = createDeferred<ITask>();
      const waitUntilTaskCompletes = spyOn(TaskReader, 'waitUntilTaskCompletes').and.returnValue(completion.promise);

      const operation = () => Promise.resolve(task);
      const monitor = new TaskMonitor({
        application: ApplicationModelBuilder.createApplicationForTests('app', {
          key: 'runningTasks',
          lazy: true,
          defaultData: [],
        }),
        title: 'some task',
        modalInstance: { result: createDeferred().promise } as IModalServiceInstance,
        monitorInterval: 1,
        onTaskComplete: () => (completeCalled = true),
      });
      spyOn(monitor.application.getDataSource('runningTasks'), 'refresh');

      monitor.submit(operation);

      expect(monitor.submitting).toBe(true);
      expect(monitor.error).toBe(false);

      await settleNativePromises();
      expect(monitor.task.isCompleted).toBe(false);
      expect((monitor.application.getDataSource('runningTasks').refresh as Spy).calls.count()).toBe(1);
      expect(waitUntilTaskCompletes).toHaveBeenCalledOnceWith(task, 1, monitor.statusUpdatedStream);

      completion.resolve(task);
      await settleNativePromises();

      expect(completeCalled).toBe(true);
    });

    it('sets error when task fails immediately', async () => {
      let completeCalled = false;
      const task = { failureMessage: 'it failed' };
      const operation = () => Promise.reject(task);
      const monitor = new TaskMonitor({
        application: ApplicationModelBuilder.createApplicationForTests('app', {
          key: 'runningTasks',
          lazy: true,
          defaultData: [],
        }),
        title: 'a task',
        modalInstance: { result: createDeferred().promise } as IModalServiceInstance,
        onTaskComplete: () => (completeCalled = true),
      });

      monitor.submit(operation);

      expect(monitor.submitting).toBe(true);

      await settleNativePromises();
      expect(monitor.submitting).toBe(false);
      expect(monitor.error).toBe(true);
      expect(monitor.errorMessage).toBe('it failed');
      expect(completeCalled).toBe(false);
    });

    it('sets error when task fails while polling', async () => {
      let completeCalled = false;
      const task = { id: 'a', status: 'RUNNING' } as ITask;
      OrchestratedItemTransformer.defineProperties(task);
      const completion = createDeferred<ITask>();
      const waitUntilTaskCompletes = spyOn(TaskReader, 'waitUntilTaskCompletes').and.returnValue(completion.promise);

      const operation = () => Promise.resolve(task);
      const monitor = new TaskMonitor({
        application: ApplicationModelBuilder.createApplicationForTests('app', {
          key: 'runningTasks',
          lazy: true,
          defaultData: [],
        }),
        title: 'a task',
        modalInstance: { result: createDeferred().promise } as IModalServiceInstance,
        monitorInterval: 1,
        onTaskComplete: () => (completeCalled = true),
      });

      monitor.submit(operation);

      expect(monitor.submitting).toBe(true);
      expect(monitor.error).toBe(false);

      await settleNativePromises();
      expect(monitor.task.isCompleted).toBe(false);
      expect(waitUntilTaskCompletes).toHaveBeenCalledOnceWith(task, 1, monitor.statusUpdatedStream);

      completion.reject(task);
      await settleNativePromises();

      expect(monitor.submitting).toBe(false);
      expect(monitor.error).toBe(true);
      expect(monitor.errorMessage).toBe('There was an unknown server error.');
      expect(completeCalled).toBe(false);
    });

    it('polls the submitted task at the configured interval until its status completes', async () => {
      jasmine.clock().install();
      try {
        const http = mockHttpClient();
        const task = { id: 'task-id', status: 'RUNNING' } as ITask;
        OrchestratedItemTransformer.defineProperties(task);
        const completed = createDeferred<void>();
        const onTaskComplete = jasmine.createSpy('onTaskComplete').and.callFake(() => completed.resolve());
        const monitor = new TaskMonitor({
          title: 'polling task',
          monitorInterval: 25,
          onTaskComplete,
        });
        http.expectGET('/tasks/task-id').respond(200, { id: 'task-id', status: 'SUCCEEDED' });

        monitor.submit(() => Promise.resolve(task));
        await settleNativePromises();

        jasmine.clock().tick(24);
        expect(http.receivedRequests).toEqual([]);
        jasmine.clock().tick(1);
        expect(http.receivedRequests.length).toBe(1);

        await http.flush();
        await completed.promise;

        expect(monitor.task).toBe(task);
        expect(monitor.task.status).toBe('SUCCEEDED');
        expect(monitor.task.isCompleted).toBe(true);
        expect(onTaskComplete).toHaveBeenCalledTimes(1);
      } finally {
        jasmine.clock().uninstall();
      }
    });
  });
});

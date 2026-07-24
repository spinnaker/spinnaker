import type { IModalServiceInstance } from 'angular-ui-bootstrap';

import { TaskMonitor } from './TaskMonitor';
import { mockHttpClient } from '../../api/mock/jasmine';
import { ApplicationModelBuilder } from '../../application/applicationModel.builder';
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
        await http.flush();
        await completed.promise;

        expect(http.receivedRequests.length).toBe(1);
        expect(monitor.task).toBe(task);
        expect(monitor.task.status).toBe('SUCCEEDED');
        expect(monitor.task.isCompleted).toBe(true);
        expect(onTaskComplete).toHaveBeenCalledTimes(1);
      } finally {
        jasmine.clock().uninstall();
      }
    });

    it('cancels the submitted task poll when the monitor closes', async () => {
      jasmine.clock().install();
      try {
        const http = mockHttpClient();
        const task = { id: 'task-id', status: 'RUNNING' } as ITask;
        OrchestratedItemTransformer.defineProperties(task);
        const monitor = new TaskMonitor({ title: 'polling task', monitorInterval: 25 });

        monitor.submit(() => Promise.resolve(task));
        await settleNativePromises();
        expect(task.poller).toBeDefined();

        monitor.onModalClose();
        jasmine.clock().tick(25);

        expect(task.poller).toBeUndefined();
        expect(http.receivedRequests).toEqual([]);
      } finally {
        jasmine.clock().uninstall();
      }
    });

    it('ignores success and rejection from submits replaced by a newer submit', async () => {
      const staleSuccess = createDeferred<ITask>();
      const staleFailure = createDeferred<ITask>();
      const activeSubmission = createDeferred<ITask>();
      const activeTask = { id: 'active-task', status: 'RUNNING' } as ITask;
      const staleTask = { id: 'stale-task', status: 'RUNNING' } as ITask;
      const polling = createDeferred<ITask>();
      const waitUntilTaskCompletes = spyOn(TaskReader, 'waitUntilTaskCompletes').and.returnValue(polling.promise);
      const application = ApplicationModelBuilder.createApplicationForTests('app', {
        key: 'runningTasks',
        lazy: true,
        defaultData: [],
      });
      const refresh = spyOn(application.getDataSource('runningTasks'), 'refresh');
      const monitor = new TaskMonitor({ application, title: 'replacement task' });

      monitor.submit(() => staleSuccess.promise);
      monitor.submit(() => staleFailure.promise);
      monitor.submit(() => activeSubmission.promise);
      activeSubmission.resolve(activeTask);
      await settleNativePromises();

      staleSuccess.resolve(staleTask);
      staleFailure.reject({ failureMessage: 'stale failure' } as ITask);
      await settleNativePromises();

      expect(monitor.task).toBe(activeTask);
      expect(monitor.error).toBe(false);
      expect(refresh).toHaveBeenCalledTimes(1);
      expect(waitUntilTaskCompletes).toHaveBeenCalledOnceWith(activeTask, 1000, monitor.statusUpdatedStream);
    });

    it('ignores terminal callbacks from polls replaced by newer generations', async () => {
      const firstTask = { id: 'first-task', status: 'RUNNING' } as ITask;
      const secondTask = { id: 'second-task', status: 'RUNNING' } as ITask;
      const activeTask = { id: 'active-task', status: 'RUNNING' } as ITask;
      const firstPoll = createDeferred<ITask>();
      const secondPoll = createDeferred<ITask>();
      const activePoll = createDeferred<ITask>();
      const onTaskComplete = jasmine.createSpy('onTaskComplete');
      spyOn(TaskReader, 'waitUntilTaskCompletes').and.callFake((task) => {
        if (task === firstTask) {
          return firstPoll.promise;
        }
        if (task === secondTask) {
          return secondPoll.promise;
        }
        return activePoll.promise;
      });
      const monitor = new TaskMonitor({ onTaskComplete, title: 'replacement poll' });

      monitor.submit(() => Promise.resolve(firstTask));
      await settleNativePromises();
      monitor.submit(() => Promise.resolve(secondTask));
      await settleNativePromises();
      monitor.submit(() => Promise.resolve(activeTask));
      await settleNativePromises();

      firstPoll.resolve(firstTask);
      secondPoll.reject(secondTask);
      await settleNativePromises();

      expect(monitor.task).toBe(activeTask);
      expect(monitor.error).toBe(false);
      expect(onTaskComplete).not.toHaveBeenCalled();

      activePoll.resolve(activeTask);
      await settleNativePromises();

      expect(onTaskComplete).toHaveBeenCalledTimes(1);
    });

    it('ignores late submit success and failure after the monitor closes', async () => {
      const lateSuccess = createDeferred<ITask>();
      const lateFailure = createDeferred<ITask>();
      const successMonitor = new TaskMonitor({ title: 'closed success' });
      const failureMonitor = new TaskMonitor({ title: 'closed failure' });
      const waitUntilTaskCompletes = spyOn(TaskReader, 'waitUntilTaskCompletes').and.returnValue(
        new Promise(() => undefined),
      );

      successMonitor.submit(() => lateSuccess.promise);
      successMonitor.onModalClose();
      failureMonitor.submit(() => lateFailure.promise);
      failureMonitor.onModalClose();

      lateSuccess.resolve({ id: 'late-task', status: 'RUNNING' } as ITask);
      lateFailure.reject({ failureMessage: 'late failure' } as ITask);
      await settleNativePromises();

      expect(successMonitor.task).toBeNull();
      expect(successMonitor.error).toBe(false);
      expect(failureMonitor.task).toBeNull();
      expect(failureMonitor.error).toBe(false);
      expect(waitUntilTaskCompletes).not.toHaveBeenCalled();
    });

    it('ignores terminal poll success and failure after the monitor closes', async () => {
      const successfulTask = { id: 'late-success', status: 'RUNNING' } as ITask;
      const failedTask = { id: 'late-failure', status: 'RUNNING' } as ITask;
      const successfulPoll = createDeferred<ITask>();
      const failedPoll = createDeferred<ITask>();
      const onSuccessfulTaskComplete = jasmine.createSpy('onSuccessfulTaskComplete');
      const onFailedTaskComplete = jasmine.createSpy('onFailedTaskComplete');
      spyOn(TaskReader, 'waitUntilTaskCompletes').and.callFake((task) =>
        task === successfulTask ? successfulPoll.promise : failedPoll.promise,
      );
      const successMonitor = new TaskMonitor({
        onTaskComplete: onSuccessfulTaskComplete,
        title: 'closed poll success',
      });
      const failureMonitor = new TaskMonitor({
        onTaskComplete: onFailedTaskComplete,
        title: 'closed poll failure',
      });

      successMonitor.submit(() => Promise.resolve(successfulTask));
      failureMonitor.submit(() => Promise.resolve(failedTask));
      await settleNativePromises();
      successMonitor.onModalClose();
      failureMonitor.onModalClose();

      successfulPoll.resolve(successfulTask);
      failedPoll.reject(failedTask);
      await settleNativePromises();

      expect(onSuccessfulTaskComplete).not.toHaveBeenCalled();
      expect(onFailedTaskComplete).not.toHaveBeenCalled();
      expect(successMonitor.error).toBe(false);
      expect(failureMonitor.error).toBe(false);
    });
  });
});

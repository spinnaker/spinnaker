import type { Mock } from 'vitest';
import { TaskMonitor } from './TaskMonitor';
import { mockHttpClient } from '../../api/mock/mockHttpSupport';
import { ApplicationModelBuilder } from '../../application/applicationModel.builder';
import type { ITask } from '../../domain';
import { OrchestratedItemTransformer } from '../../orchestratedItem/orchestratedItem.transformer';
import { TaskReader } from '../task.read.service';
import { createDeferred } from '../../utils/deferred';

import Spy = Mock;

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
      const waitUntilTaskCompletes = vi.spyOn(TaskReader, 'waitUntilTaskCompletes').mockReturnValue(completion.promise);

      const operation = () => Promise.resolve(task);
      const monitor = new TaskMonitor({
        application: ApplicationModelBuilder.createApplicationForTests('app', {
          key: 'runningTasks',
          lazy: true,
          defaultData: [],
        }),
        title: 'some task',
        monitorInterval: 1,
        onTaskComplete: () => (completeCalled = true),
      });
      vi.spyOn(monitor.application.getDataSource('runningTasks'), 'refresh').mockReturnValue(undefined);

      monitor.submit(operation);

      expect(monitor.submitting).toBe(true);
      expect(monitor.error).toBe(false);

      await settleNativePromises();
      expect(monitor.task.isCompleted).toBe(false);
      expect((monitor.application.getDataSource('runningTasks').refresh as Spy).mock.calls.length).toBe(1);
      expect(waitUntilTaskCompletes).toHaveBeenCalledExactlyOnceWith(task, 1, monitor.statusUpdatedStream);

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
      const waitUntilTaskCompletes = vi.spyOn(TaskReader, 'waitUntilTaskCompletes').mockReturnValue(completion.promise);

      const operation = () => Promise.resolve(task);
      const monitor = new TaskMonitor({
        application: ApplicationModelBuilder.createApplicationForTests('app', {
          key: 'runningTasks',
          lazy: true,
          defaultData: [],
        }),
        title: 'a task',
        monitorInterval: 1,
        onTaskComplete: () => (completeCalled = true),
      });

      monitor.submit(operation);

      expect(monitor.submitting).toBe(true);
      expect(monitor.error).toBe(false);

      await settleNativePromises();
      expect(monitor.task.isCompleted).toBe(false);
      expect(waitUntilTaskCompletes).toHaveBeenCalledExactlyOnceWith(task, 1, monitor.statusUpdatedStream);

      completion.reject(task);
      await settleNativePromises();

      expect(monitor.submitting).toBe(false);
      expect(monitor.error).toBe(true);
      expect(monitor.errorMessage).toBe('There was an unknown server error.');
      expect(completeCalled).toBe(false);
    });

    it('polls the submitted task at the configured interval until its status completes', async () => {
      vi.useFakeTimers({
        toFake: ['setTimeout', 'clearTimeout', 'setInterval', 'clearInterval'],
      });
      try {
        const http = mockHttpClient();
        const task = { id: 'task-id', status: 'RUNNING' } as ITask;
        OrchestratedItemTransformer.defineProperties(task);
        const completed = createDeferred<void>();
        const onTaskComplete = vi.fn().mockImplementation(() => completed.resolve());
        const monitor = new TaskMonitor({
          title: 'polling task',
          monitorInterval: 25,
          onTaskComplete,
        });
        http.expectGET('/tasks/task-id').respond(200, { id: 'task-id', status: 'SUCCEEDED' });

        monitor.submit(() => Promise.resolve(task));
        await settleNativePromises();

        vi.advanceTimersByTime(24);
        expect(http.receivedRequests).toEqual([]);
        vi.advanceTimersByTime(1);
        await http.flush();
        await completed.promise;

        expect(http.receivedRequests.length).toBe(1);
        expect(monitor.task).toBe(task);
        expect(monitor.task.status).toBe('SUCCEEDED');
        expect(monitor.task.isCompleted).toBe(true);
        expect(onTaskComplete).toHaveBeenCalledTimes(1);
      } finally {
        vi.useRealTimers();
      }
    });

    it('cancels the submitted task poll when the monitor closes', async () => {
      vi.useFakeTimers({
        toFake: ['setTimeout', 'clearTimeout', 'setInterval', 'clearInterval'],
      });
      try {
        const http = mockHttpClient();
        const task = { id: 'task-id', status: 'RUNNING' } as ITask;
        OrchestratedItemTransformer.defineProperties(task);
        const onDismiss = vi.fn();
        const monitor = new TaskMonitor({ title: 'polling task', monitorInterval: 25, onDismiss });

        monitor.submit(() => Promise.resolve(task));
        await settleNativePromises();
        expect(task.poller).toBeDefined();

        monitor.closeModal();
        vi.advanceTimersByTime(25);

        expect(task.poller).toBeUndefined();
        expect(http.receivedRequests).toEqual([]);
        expect(onDismiss).toHaveBeenCalledTimes(1);
      } finally {
        vi.useRealTimers();
      }
    });

    it('ignores success and rejection from submits replaced by a newer submit', async () => {
      const staleSuccess = createDeferred<ITask>();
      const staleFailure = createDeferred<ITask>();
      const activeSubmission = createDeferred<ITask>();
      const activeTask = { id: 'active-task', status: 'RUNNING' } as ITask;
      const staleTask = { id: 'stale-task', status: 'RUNNING' } as ITask;
      const polling = createDeferred<ITask>();
      const waitUntilTaskCompletes = vi.spyOn(TaskReader, 'waitUntilTaskCompletes').mockReturnValue(polling.promise);
      const application = ApplicationModelBuilder.createApplicationForTests('app', {
        key: 'runningTasks',
        lazy: true,
        defaultData: [],
      });
      const refresh = vi.spyOn(application.getDataSource('runningTasks'), 'refresh').mockReturnValue(undefined);
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
      expect(waitUntilTaskCompletes).toHaveBeenCalledExactlyOnceWith(activeTask, 1000, monitor.statusUpdatedStream);
    });

    it('ignores terminal callbacks from polls replaced by newer generations', async () => {
      const firstTask = { id: 'first-task', status: 'RUNNING' } as ITask;
      const secondTask = { id: 'second-task', status: 'RUNNING' } as ITask;
      const activeTask = { id: 'active-task', status: 'RUNNING' } as ITask;
      const firstPoll = createDeferred<ITask>();
      const secondPoll = createDeferred<ITask>();
      const activePoll = createDeferred<ITask>();
      const onTaskComplete = vi.fn();
      vi.spyOn(TaskReader, 'waitUntilTaskCompletes').mockImplementation((task) => {
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
      const waitUntilTaskCompletes = vi
        .spyOn(TaskReader, 'waitUntilTaskCompletes')
        .mockReturnValue(new Promise(() => undefined));

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
      const onSuccessfulTaskComplete = vi.fn();
      const onFailedTaskComplete = vi.fn();
      vi.spyOn(TaskReader, 'waitUntilTaskCompletes').mockImplementation((task) =>
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

  describe('close', () => {
    it('stops event propagation and invokes the direct dismiss handler', () => {
      const stopPropagation = vi.fn();
      const onDismiss = vi.fn();
      const monitor = new TaskMonitor({ title: 'dismissable task', onDismiss });

      monitor.closeModal({ stopPropagation } as any);

      expect(stopPropagation).toHaveBeenCalledTimes(1);
      expect(onDismiss).toHaveBeenCalledExactlyOnceWith();
    });

    it('invalidates a pending submission before dismissing', async () => {
      const submission = createDeferred<ITask>();
      const waitUntilTaskCompletes = vi.spyOn(TaskReader, 'waitUntilTaskCompletes').mockReturnValue(undefined);
      const onDismiss = vi.fn();
      const monitor = new TaskMonitor({ title: 'pending task', onDismiss });

      monitor.submit(() => submission.promise);
      monitor.closeModal();
      submission.resolve({ id: 'late-task', status: 'RUNNING' } as ITask);
      await settleNativePromises();

      expect(monitor.task).toBeNull();
      expect(waitUntilTaskCompletes).not.toHaveBeenCalled();
      expect(onDismiss).toHaveBeenCalledTimes(1);
    });

    it('cancels and dismisses only once when closed repeatedly', () => {
      const onDismiss = vi.fn();
      const cancelPolling = vi.spyOn(TaskReader, 'cancelPolling').mockReturnValue(undefined);
      const monitor = new TaskMonitor({ title: 'idempotent task', onDismiss });

      monitor.closeModal();
      monitor.closeModal();

      expect(cancelPolling).toHaveBeenCalledTimes(1);
      expect(onDismiss).toHaveBeenCalledTimes(1);
    });

    it('does not swallow dismiss handler exceptions', () => {
      const error = new Error('dismiss failed');
      const monitor = new TaskMonitor({
        title: 'failing dismissal',
        onDismiss: () => {
          throw error;
        },
      });

      expect(() => monitor.closeModal()).toThrow(error);
    });
  });
});

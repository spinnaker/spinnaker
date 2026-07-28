import type { IConfirmModalProps } from './ConfirmModal';
import { ConfirmationModalService } from './confirmationModal.service';
import type { ITask } from '../domain';
import { ReactModal } from '../presentation/ReactModal';
import type { TaskMonitor } from '../task';
import { TaskReader } from '../task/task.read.service';

const createDeferred = <T>() => {
  let resolve: (value: T | PromiseLike<T>) => void;
  let reject: (reason?: unknown) => void;
  const promise = new Promise<T>((promiseResolve, promiseReject) => {
    resolve = promiseResolve;
    reject = promiseReject;
  });

  return { promise, reject, resolve };
};

describe('ConfirmationModalService', () => {
  it('resolves the direct modal result', async () => {
    const modalPromise = Promise.resolve('confirmed');
    spyOn(ReactModal, 'show').and.returnValue(modalPromise);

    const confirmation = ConfirmationModalService.confirm({ header: 'Rollout restart' });

    expect(confirmation).toBe(modalPromise);
    expect(await confirmation).toBe('confirmed');
    expect(ReactModal.show).toHaveBeenCalled();
  });

  it('cleans task polling when the native modal resolves and returns the same promise', async () => {
    const modal = createDeferred<string>();
    const show = spyOn(ReactModal, 'show').and.returnValue(modal.promise);

    const confirmation = ConfirmationModalService.confirm({
      header: 'Rollout restart',
      taskMonitorConfig: { title: 'Restarting instances' },
    });
    const taskMonitor = (show.calls.mostRecent().args[1] as IConfirmModalProps).taskMonitor as TaskMonitor;
    const onModalClose = spyOn(taskMonitor, 'onModalClose');

    expect(confirmation).toBe(modal.promise);
    modal.resolve('confirmed');

    expect(await confirmation).toBe('confirmed');
    expect(onModalClose).toHaveBeenCalledTimes(1);

    modal.reject(new Error('late dismissal'));
    await Promise.resolve();
    expect(onModalClose).toHaveBeenCalledTimes(1);
  });

  it('cleans task polling when the native modal rejects and preserves the rejection', async () => {
    const modal = createDeferred<string>();
    const show = spyOn(ReactModal, 'show').and.returnValue(modal.promise);
    const dismissal = new Error('dismissed');

    const confirmation = ConfirmationModalService.confirm({
      header: 'Rollout restart',
      taskMonitorConfig: { title: 'Restarting instances' },
    });
    const taskMonitor = (show.calls.mostRecent().args[1] as IConfirmModalProps).taskMonitor as TaskMonitor;
    const onModalClose = spyOn(taskMonitor, 'onModalClose');
    const rejection = confirmation.then(
      () => Promise.reject(new Error('Expected confirmation to reject')),
      (reason) => reason,
    );

    expect(confirmation).toBe(modal.promise);
    modal.reject(dismissal);

    expect(await rejection).toBe(dismissal);
    expect(onModalClose).toHaveBeenCalledTimes(1);
  });

  it('does not start polling when a pending submission resolves after native modal dismissal', async () => {
    const modal = createDeferred<string>();
    const submission = createDeferred<ITask>();
    const show = spyOn(ReactModal, 'show').and.returnValue(modal.promise);
    const waitUntilTaskCompletes = spyOn(TaskReader, 'waitUntilTaskCompletes').and.returnValue(
      new Promise(() => undefined),
    );

    const confirmation = ConfirmationModalService.confirm({
      header: 'Rollout restart',
      taskMonitorConfig: { title: 'Restarting instances' },
    });
    const taskMonitor = (show.calls.mostRecent().args[1] as IConfirmModalProps).taskMonitor as TaskMonitor;
    taskMonitor.submit(() => submission.promise);
    const dismissal = confirmation.then(
      () => Promise.reject(new Error('Expected confirmation to reject')),
      (reason) => reason,
    );

    modal.reject('dismissed');
    expect(await dismissal).toBe('dismissed');

    submission.resolve({ id: 'late-task' } as ITask);
    await Promise.resolve();
    await Promise.resolve();

    expect(waitUntilTaskCompletes).not.toHaveBeenCalled();
    expect(taskMonitor.task).toBeNull();
  });
});

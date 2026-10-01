import { UIRouterContext, UIRouterReact } from '@uirouter/react';
import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import React from 'react';
import { filter, take } from 'rxjs/operators';

import { ConfirmModal } from './ConfirmModal';
import type { ITask } from '../domain';
import { TaskMonitor, TaskReader } from '../task';

describe('ConfirmModal', () => {
  it('submits the exact reason entered through the public controls', async () => {
    const submitMethod = vi.fn().mockResolvedValue(undefined);
    const closeModal = vi.fn();
    render(
      <ConfirmModal
        header="Page payments Owner"
        buttonText="Page Owner"
        cancelButtonText="Cancel"
        reasonPlaceholder="Why is the owner being paged?"
        submitJustWithReason={true}
        submitMethod={submitMethod}
        closeModal={closeModal}
        dismissModal={vi.fn()}
      />,
    );

    await userEvent.type(screen.getByPlaceholderText('Why is the owner being paged?'), 'Production outage');
    await userEvent.click(screen.getByRole('button', { name: 'Page Owner' }));

    expect(submitMethod).toHaveBeenCalledTimes(1);
    expect(submitMethod).toHaveBeenCalledWith({ reason: 'Production outage' });
    await act(async () => Promise.resolve());
    expect(closeModal).toHaveBeenCalledTimes(1);
  });

  it('dismisses from the footer when the visible cancel button is clicked', async () => {
    const dismissModal = vi.fn();
    render(
      <ConfirmModal
        header="Page payments Owner"
        buttonText="Page Owner"
        cancelButtonText="Cancel"
        closeModal={vi.fn()}
        dismissModal={dismissModal}
      />,
    );

    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));

    expect(dismissModal).toHaveBeenCalledTimes(1);
    expect(dismissModal).toHaveBeenCalledWith({ source: 'footer' });
  });

  it('requires a reason and uses the configured placeholder', async () => {
    const taskMonitor = new TaskMonitor({ title: 'Page application owner', onDismiss: () => undefined });
    render(
      <ConfirmModal
        header="Page payments Owner"
        buttonText="Page Owner"
        cancelButtonText="Cancel"
        askForReason={true}
        reasonRequired={true}
        reasonPlaceholder="Why is the owner being paged?"
        submitMethod={vi.fn().mockResolvedValue(undefined)}
        closeModal={vi.fn()}
        dismissModal={vi.fn()}
        taskMonitor={taskMonitor}
      />,
    );
    const submit = screen.getByRole('button', { name: 'Page Owner' });
    const reason = screen.getByPlaceholderText('Why is the owner being paged?');

    expect(submit).toBeDisabled();
    await userEvent.type(reason, '   ');
    expect(submit).toBeDisabled();
    await userEvent.clear(reason);
    await userEvent.type(reason, 'Production outage');
    expect(submit).toBeEnabled();
  });

  it('resets submitting after a task rejection when retry has no original callback', async () => {
    let rejectSubmission: (reason: unknown) => void;
    const submission = new Promise((_resolve, reject) => (rejectSubmission = reject));
    const taskMonitor = new TaskMonitor({ title: 'Page application owner' });
    const router = new UIRouterReact();
    render(
      <UIRouterContext.Provider value={router}>
        <ConfirmModal
          header="Page payments Owner"
          buttonText="Page Owner"
          cancelButtonText="Cancel"
          submitMethod={vi.fn().mockReturnValue(submission)}
          closeModal={vi.fn()}
          dismissModal={vi.fn()}
          taskMonitor={taskMonitor}
        />
      </UIRouterContext.Provider>,
    );

    expect(taskMonitor.hasDismissHandler()).toBe(false);
    await userEvent.click(screen.getByRole('button', { name: 'Page Owner' }));
    expect(screen.getByRole('button', { name: 'Page Owner' })).toBeDisabled();

    const errorPublished = taskMonitor.statusUpdatedStream
      .pipe(
        filter(() => taskMonitor.error),
        take(1),
      )
      .toPromise();
    await act(async () => {
      rejectSubmission({ failureMessage: 'Page request failed' });
      await errorPublished;
    });

    expect(taskMonitor.error).toBe(true);
    await userEvent.click(screen.getByRole('button', { name: 'Go back and try to fix this' }));

    expect(taskMonitor.error).toBeNull();
    expect(screen.getByRole('button', { name: 'Page Owner' })).toBeEnabled();
    router.dispose();
  });

  it('installs a local close override and restores it on unmount when no dismiss handler exists', () => {
    const dismissModal = vi.fn();
    const stopPropagation = vi.fn();
    const taskMonitor = new TaskMonitor({ title: 'Page application owner' });
    const originalCloseModal = taskMonitor.closeModal;
    const { unmount } = render(
      <ConfirmModal
        header="Page payments Owner"
        buttonText="Page Owner"
        cancelButtonText="Cancel"
        closeModal={vi.fn()}
        dismissModal={dismissModal}
        taskMonitor={taskMonitor}
      />,
    );

    expect(taskMonitor.closeModal).not.toBe(originalCloseModal);
    taskMonitor.closeModal({ stopPropagation } as any);
    expect(stopPropagation).toHaveBeenCalledTimes(1);
    expect(dismissModal).toHaveBeenCalledTimes(1);

    unmount();
    expect(taskMonitor.closeModal).toBe(originalCloseModal);
  });

  it('closes the task monitor before dismissing and dismisses only once when dismissal throws', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'setInterval', 'clearInterval'] });
    const poll = vi.fn();
    const activeTask = { poller: setTimeout(poll, 25) } as ITask;
    const lateTask = { id: 'late-task', status: 'RUNNING' } as ITask;
    let resolveSubmission: (task: ITask) => void;
    const submission = new Promise<ITask>((resolve) => (resolveSubmission = resolve));
    const waitUntilTaskCompletes = vi.spyOn(TaskReader, 'waitUntilTaskCompletes').mockReturnValue(undefined);
    const dismissalError = new Error('dismiss failed');
    let pollingWasActiveAtDismiss: boolean;
    const taskMonitor = new TaskMonitor({ title: 'Page application owner' });
    const dismissModal = vi.fn().mockImplementation(() => {
      pollingWasActiveAtDismiss = activeTask.poller !== undefined;
      resolveSubmission(lateTask);
      throw dismissalError;
    });
    const router = new UIRouterReact();
    const { unmount } = render(
      <UIRouterContext.Provider value={router}>
        <ConfirmModal
          header="Page payments Owner"
          buttonText="Page Owner"
          cancelButtonText="Cancel"
          closeModal={vi.fn()}
          dismissModal={dismissModal}
          taskMonitor={taskMonitor}
        />
      </UIRouterContext.Provider>,
    );

    try {
      taskMonitor.submit(() => submission);
      taskMonitor.task = activeTask;

      expect(() => taskMonitor.closeModal()).toThrow(dismissalError);
      await Promise.resolve();
      await Promise.resolve();

      expect(pollingWasActiveAtDismiss).toBe(false);
      expect(activeTask.poller).toBeUndefined();
      vi.advanceTimersByTime(25);
      expect(poll).not.toHaveBeenCalled();
      expect(waitUntilTaskCompletes).not.toHaveBeenCalled();
      expect(taskMonitor.task).toBe(activeTask);
      expect(() => taskMonitor.closeModal()).not.toThrow();
      expect(dismissModal).toHaveBeenCalledTimes(1);
    } finally {
      unmount();
      router.dispose();
      vi.useRealTimers();
    }
  });

  it('keeps the task monitor close handler when it has a direct dismiss handler', () => {
    const onDismiss = vi.fn();
    const dismissModal = vi.fn();
    const taskMonitor = new TaskMonitor({ title: 'Page application owner', onDismiss });
    const originalCloseModal = taskMonitor.closeModal;
    const { unmount } = render(
      <ConfirmModal
        header="Page payments Owner"
        buttonText="Page Owner"
        cancelButtonText="Cancel"
        closeModal={vi.fn()}
        dismissModal={dismissModal}
        taskMonitor={taskMonitor}
      />,
    );

    expect(taskMonitor.closeModal).toBe(originalCloseModal);
    taskMonitor.closeModal();
    expect(onDismiss).toHaveBeenCalledTimes(1);
    expect(dismissModal).not.toHaveBeenCalled();
    unmount();
  });
});

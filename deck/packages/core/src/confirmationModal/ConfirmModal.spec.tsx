import { UIRouterContext, UIRouterReact } from '@uirouter/react';
import { mount } from 'enzyme';
import React from 'react';
import { act } from 'react-dom/test-utils';
import { filter, take } from 'rxjs/operators';

import { ConfirmModal } from './ConfirmModal';
import { TaskMonitor, TaskReader } from '../task';
import type { ITask } from '../domain';

describe('ConfirmModal', () => {
  it('requires a reason and uses the configured placeholder', () => {
    const submitMethod = jasmine.createSpy('submitMethod').and.returnValue(Promise.resolve());
    const closeModal = jasmine.createSpy('closeModal');
    const dismissModal = jasmine.createSpy('dismissModal');
    const taskMonitor = new TaskMonitor({
      title: 'Page application owner',
      onDismiss: () => undefined,
    });
    const wrapper = mount(
      <ConfirmModal
        header="Page payments Owner"
        buttonText="Page Owner"
        cancelButtonText="Cancel"
        askForReason={true}
        reasonRequired={true}
        reasonPlaceholder="Why is the owner being paged?"
        submitMethod={submitMethod}
        closeModal={closeModal}
        dismissModal={dismissModal}
        taskMonitor={taskMonitor}
      />,
    );

    expect(wrapper.find('button.btn-primary').prop('disabled')).toBe(true);
    wrapper.find('textarea').simulate('change', { target: { value: '   ' } });
    wrapper.update();
    expect(wrapper.find('button.btn-primary').prop('disabled')).toBe(true);
    wrapper.find('textarea').simulate('change', { target: { value: 'Production outage' } });
    wrapper.update();
    expect(wrapper.find('button.btn-primary').prop('disabled')).toBe(false);
    expect(wrapper.find('textarea').prop('placeholder')).toBe('Why is the owner being paged?');

    wrapper.unmount();
  });

  it('resets submitting after a task rejection when retry has no original callback', async () => {
    let rejectSubmission: (reason: unknown) => void;
    const submission = new Promise((_resolve, reject) => {
      rejectSubmission = reject;
    });
    const submitMethod = jasmine.createSpy('submitMethod').and.returnValue(submission);
    const taskMonitor = new TaskMonitor({ title: 'Page application owner' });
    const router = new UIRouterReact();
    const wrapper = mount(
      <UIRouterContext.Provider value={router}>
        <ConfirmModal
          header="Page payments Owner"
          buttonText="Page Owner"
          cancelButtonText="Cancel"
          submitMethod={submitMethod}
          closeModal={jasmine.createSpy('closeModal')}
          dismissModal={jasmine.createSpy('dismissModal')}
          taskMonitor={taskMonitor}
        />
      </UIRouterContext.Provider>,
    );

    expect(taskMonitor.hasDismissHandler()).toBe(false);
    act(() => {
      wrapper.find('button.btn-primary').last().simulate('click');
    });
    wrapper.update();

    expect(wrapper.find('button.btn-primary .load.nano').exists()).toBe(true);
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
    wrapper.update();

    expect(taskMonitor.error).toBe(true);
    expect(wrapper.find('.overlay-modal-error').exists()).toBe(true);

    act(() => {
      wrapper
        .find('button')
        .filterWhere((button) => button.text() === 'Go back and try to fix this')
        .simulate('click');
    });
    wrapper.update();

    expect(taskMonitor.error).toBeNull();
    expect(wrapper.find('.overlay-modal-error').exists()).toBe(false);
    expect(wrapper.find('button.btn-primary').last().prop('disabled')).toBe(false);

    wrapper.unmount();
    router.dispose();
  });

  it('installs a local close override when the task monitor has no dismiss handler', () => {
    const dismissModal = jasmine.createSpy('dismissModal');
    const stopPropagation = jasmine.createSpy('stopPropagation');
    const taskMonitor = new TaskMonitor({ title: 'Page application owner' });
    const originalCloseModal = taskMonitor.closeModal;
    const wrapper = mount(
      <ConfirmModal
        header="Page payments Owner"
        buttonText="Page Owner"
        cancelButtonText="Cancel"
        closeModal={jasmine.createSpy('closeModal')}
        dismissModal={dismissModal}
        taskMonitor={taskMonitor}
      />,
    );

    expect(taskMonitor.closeModal).not.toBe(originalCloseModal);
    taskMonitor.closeModal({ stopPropagation } as any);

    expect(stopPropagation).toHaveBeenCalledTimes(1);
    expect(dismissModal).toHaveBeenCalledTimes(1);

    wrapper.unmount();
    expect(taskMonitor.closeModal).toBe(originalCloseModal);
  });

  it('closes the task monitor before dismissing and dismisses only once when dismissal throws', async () => {
    jasmine.clock().install();
    const poll = jasmine.createSpy('poll');
    const activeTask = { poller: setTimeout(poll, 25) } as ITask;
    const lateTask = { id: 'late-task', status: 'RUNNING' } as ITask;
    let resolveSubmission: (task: ITask) => void;
    const submission = new Promise<ITask>((resolve) => (resolveSubmission = resolve));
    const waitUntilTaskCompletes = spyOn(TaskReader, 'waitUntilTaskCompletes');
    const dismissalError = new Error('dismiss failed');
    let pollingWasActiveAtDismiss: boolean;
    const taskMonitor = new TaskMonitor({ title: 'Page application owner' });
    const router = new UIRouterReact();
    const dismissModal = jasmine.createSpy('dismissModal').and.callFake(() => {
      pollingWasActiveAtDismiss = activeTask.poller !== undefined;
      resolveSubmission(lateTask);
      throw dismissalError;
    });
    const wrapper = mount(
      <UIRouterContext.Provider value={router}>
        <ConfirmModal
          header="Page payments Owner"
          buttonText="Page Owner"
          cancelButtonText="Cancel"
          closeModal={jasmine.createSpy('closeModal')}
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
      jasmine.clock().tick(25);
      expect(poll).not.toHaveBeenCalled();
      expect(waitUntilTaskCompletes).not.toHaveBeenCalled();
      expect(taskMonitor.task).toBe(activeTask);

      expect(() => taskMonitor.closeModal()).not.toThrow();
      expect(dismissModal).toHaveBeenCalledTimes(1);
    } finally {
      wrapper.unmount();
      router.dispose();
      jasmine.clock().uninstall();
    }
  });

  it('keeps the task monitor close handler when it has a direct dismiss handler', () => {
    const onDismiss = jasmine.createSpy('onDismiss');
    const dismissModal = jasmine.createSpy('dismissModal');
    const taskMonitor = new TaskMonitor({ title: 'Page application owner', onDismiss });
    const originalCloseModal = taskMonitor.closeModal;
    const wrapper = mount(
      <ConfirmModal
        header="Page payments Owner"
        buttonText="Page Owner"
        cancelButtonText="Cancel"
        closeModal={jasmine.createSpy('closeModal')}
        dismissModal={dismissModal}
        taskMonitor={taskMonitor}
      />,
    );

    expect(taskMonitor.closeModal).toBe(originalCloseModal);
    taskMonitor.closeModal();

    expect(onDismiss).toHaveBeenCalledTimes(1);
    expect(dismissModal).not.toHaveBeenCalled();

    wrapper.unmount();
  });
});

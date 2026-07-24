import { UIRouterContext, UIRouterReact } from '@uirouter/react';
import { mount } from 'enzyme';
import React from 'react';
import { act } from 'react-dom/test-utils';
import { filter, take } from 'rxjs/operators';

import { ConfirmModal } from './ConfirmModal';
import { TaskMonitor } from '../task';

describe('ConfirmModal', () => {
  it('requires a reason and uses the configured placeholder', () => {
    const submitMethod = jasmine.createSpy('submitMethod').and.returnValue(Promise.resolve());
    const closeModal = jasmine.createSpy('closeModal');
    const dismissModal = jasmine.createSpy('dismissModal');
    const taskMonitor = new TaskMonitor({
      title: 'Page application owner',
      modalInstance: { result: new Promise(() => undefined) } as any,
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

    expect(taskMonitor.modalInstance).toBeFalsy();
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
});

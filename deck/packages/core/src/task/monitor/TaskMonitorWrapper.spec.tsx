import { mount } from 'enzyme';
import React from 'react';

import { TaskMonitor } from './TaskMonitor';
import { TaskMonitorWrapper } from './TaskMonitorWrapper';
import type { ITask } from '../../domain';

describe('TaskMonitorWrapper', () => {
  it('keeps an idle monitor available when its wrapper unmounts during a parent rerender', () => {
    const monitor = new TaskMonitor({ title: 'idle task monitor' });
    const onModalClose = spyOn(monitor, 'onModalClose').and.callThrough();
    const wrapper = mount(<TaskMonitorWrapper monitor={monitor} />);

    wrapper.unmount();

    expect(onModalClose).not.toHaveBeenCalled();
  });

  it('cancels monitor polling when its React owner unmounts', () => {
    jasmine.clock().install();
    try {
      const poll = jasmine.createSpy('poll');
      const task = { poller: setTimeout(poll, 25) } as ITask;
      const monitor = new TaskMonitor({ title: 'owned task monitor' });
      monitor.task = task;
      const wrapper = mount(<TaskMonitorWrapper monitor={monitor} />);

      wrapper.unmount();
      jasmine.clock().tick(25);

      expect(task.poller).toBeUndefined();
      expect(poll).not.toHaveBeenCalled();
    } finally {
      jasmine.clock().uninstall();
    }
  });
});

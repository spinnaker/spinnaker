import { render } from '@testing-library/react';
import React from 'react';

import { TaskMonitor } from './TaskMonitor';
import { TaskMonitorWrapper } from './TaskMonitorWrapper';
import type { ITask } from '../../domain';

describe('TaskMonitorWrapper', () => {
  it('keeps an idle monitor available when its wrapper unmounts during a parent rerender', () => {
    const monitor = new TaskMonitor({ title: 'idle task monitor' });
    const onModalClose = vi.spyOn(monitor, 'onModalClose');
    const { unmount } = render(<TaskMonitorWrapper monitor={monitor} />);

    unmount();

    expect(onModalClose).not.toHaveBeenCalled();
  });

  it('cancels monitor polling when its React owner unmounts', () => {
    vi.useFakeTimers({
      toFake: ['setTimeout', 'clearTimeout', 'setInterval', 'clearInterval'],
    });
    try {
      const poll = vi.fn();
      const task = { poller: setTimeout(poll, 25) } as ITask;
      const onDismiss = vi.fn();
      const monitor = new TaskMonitor({ title: 'owned task monitor', onDismiss });
      monitor.task = task;
      const { unmount } = render(<TaskMonitorWrapper monitor={monitor} />);

      unmount();
      vi.advanceTimersByTime(25);

      expect(task.poller).toBeUndefined();
      expect(poll).not.toHaveBeenCalled();
      expect(onDismiss).not.toHaveBeenCalled();
    } finally {
      vi.useRealTimers();
    }
  });
});

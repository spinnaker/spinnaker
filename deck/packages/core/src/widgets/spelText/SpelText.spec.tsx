import { mount } from 'enzyme';
import React from 'react';

import { SpelText } from './SpelText';
import type { CancellableTimeout } from '../../utils/cancellableTimeout';

describe('SpelText', () => {
  it('disposes its timeout owner on unmount so a pending callback cannot run', () => {
    jasmine.clock().install();
    try {
      const callback = jasmine.createSpy('callback');
      const wrapper = mount(
        <SpelText placeholder="" value="" onChange={() => undefined} pipeline={{} as any} docLink={false} />,
      );
      const timeoutService = (wrapper.instance() as any).timeoutService as CancellableTimeout;
      const dispose = spyOn(timeoutService, 'dispose').and.callThrough();
      timeoutService(callback, 25);

      wrapper.unmount();
      jasmine.clock().tick(25);

      expect(dispose).toHaveBeenCalledTimes(1);
      expect(callback).not.toHaveBeenCalled();
    } finally {
      jasmine.clock().uninstall();
    }
  });
});

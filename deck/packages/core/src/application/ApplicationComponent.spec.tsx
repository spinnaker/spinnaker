import { shallow } from 'enzyme';
import React from 'react';

import { ApplicationComponent } from './ApplicationComponent';
import type { Application } from './application.model';

describe('<ApplicationComponent />', () => {
  it('does not remount the same app when child route props refresh', () => {
    const unsubscribeRefresh = vi.fn();
    const app = ({
      attributes: {},
      disableAutoRefresh: vi.fn(),
      enableAutoRefresh: vi.fn(),
      name: 'kubernetesapp',
      subscribeToRefresh: vi.fn().mockReturnValue(unsubscribeRefresh),
    } as unknown) as Application;

    const wrapper = shallow(<ApplicationComponent app={app} />);

    expect(app.enableAutoRefresh).toHaveBeenCalledTimes(1);

    wrapper.setProps({ app });

    expect(app.disableAutoRefresh).not.toHaveBeenCalled();
    expect(app.enableAutoRefresh).toHaveBeenCalledTimes(1);

    wrapper.unmount();

    expect(unsubscribeRefresh).toHaveBeenCalledTimes(1);
    expect(app.disableAutoRefresh).toHaveBeenCalledTimes(1);
  });
});

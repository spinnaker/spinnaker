import { shallow } from 'enzyme';
import React from 'react';

import { ApplicationComponent } from './ApplicationComponent';
import type { Application } from './application.model';

describe('<ApplicationComponent />', () => {
  it('does not remount the same app when child route props refresh', () => {
    const unsubscribeRefresh = jasmine.createSpy('unsubscribeRefresh');
    const app = ({
      attributes: {},
      disableAutoRefresh: jasmine.createSpy('disableAutoRefresh'),
      enableAutoRefresh: jasmine.createSpy('enableAutoRefresh'),
      name: 'kubernetesapp',
      subscribeToRefresh: jasmine.createSpy('subscribeToRefresh').and.returnValue(unsubscribeRefresh),
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

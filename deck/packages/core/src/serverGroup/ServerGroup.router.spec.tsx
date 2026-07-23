import { shallow } from 'enzyme';
import React from 'react';

import { ServerGroupComponent } from './ServerGroup';

describe('server group router bridge', () => {
  const application = {} as any;
  const serverGroup = {
    account: 'test-account',
    buildInfo: { images: [] },
    instances: [],
    name: 'test-v001',
    region: 'test-region',
    type: 'kubernetes',
  } as any;
  const sortFilter = { multiselect: false, showAllInstances: false } as any;

  const props = (includes: jasmine.Spy) =>
    ({
      application,
      cluster: 'test',
      hasDiscovery: false,
      hasLoadBalancers: false,
      router: {},
      serverGroup,
      sortFilter,
      stateParams: {},
      stateService: { includes },
    } as any);

  it('selects a server group through the injected state service', () => {
    const includes = jasmine.createSpy('includes').and.returnValue(true);
    const component = shallow(<ServerGroupComponent {...props(includes)} />, { disableLifecycleMethods: true });

    expect(component.state('isSelected')).toBe(true);
    expect(includes).toHaveBeenCalledWith('**.serverGroup', {
      accountId: 'test-account',
      provider: 'kubernetes',
      region: 'test-region',
      serverGroup: 'test-v001',
    });
  });
});

import { shallow } from 'enzyme';
import React from 'react';
import { Subject } from 'rxjs';

import { ServerGroupComponent } from './ServerGroup';
import { ClusterState } from '../state';

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
  it('renders server group multiselection from the existing selection stream', () => {
    const previousMultiselectModel = ClusterState.multiselectModel;
    const previousFilterService = ClusterState.filterService;
    const serverGroupsStream = new Subject<void>();
    const instancesStream = new Subject<void>();
    const serverGroupIsSelected = jasmine.createSpy('serverGroupIsSelected').and.returnValue(false);
    ClusterState.multiselectModel = { serverGroupsStream, instancesStream, serverGroupIsSelected } as any;
    ClusterState.filterService = { shouldShowInstance: () => true } as any;
    const selectedServerGroup = {
      ...serverGroup,
      instances: [{ name: 'test-v001-0', buildInfo: { images: [] } }],
    } as any;
    const streamProps = {
      ...props(jasmine.createSpy('includes').and.returnValue(false)),
      router: { transitionService: { onSuccess: () => () => undefined } },
      serverGroup: selectedServerGroup,
      sortFilter: { ...sortFilter, multiselect: true },
    } as any;

    let serverGroupWrapper: any;
    try {
      serverGroupWrapper = shallow(<ServerGroupComponent {...streamProps} />);

      serverGroupIsSelected.and.returnValue(true);
      serverGroupsStream.next();
      serverGroupWrapper.update();

      expect(serverGroupWrapper.state('isMultiSelected')).toBe(true);
    } finally {
      serverGroupWrapper?.unmount();
      ClusterState.multiselectModel = previousMultiselectModel;
      ClusterState.filterService = previousFilterService;
    }

    expect(serverGroupsStream.observers.length).toBe(0);
  });
});

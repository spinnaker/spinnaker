import { act, render } from '@testing-library/react';
import React from 'react';
import { Subject } from 'rxjs';
import type { Mock } from 'vitest';

import { ServerGroupComponent } from './ServerGroup';
import { ClusterState } from '../state';

describe('server group router bridge', () => {
  const application = {} as any;
  const serverGroup = {
    account: 'test-account',
    buildInfo: { images: [] },
    instanceCounts: { down: 0, up: 0 },
    instances: [],
    moniker: { sequence: 1 },
    name: 'test-v001',
    region: 'test-region',
    runningExecutions: [],
    runningTasks: [],
    type: 'kubernetes',
  } as any;
  const sortFilter = { listInstances: false, multiselect: false, showAllInstances: false } as any;
  let previousMultiselectModel: any;
  let previousFilterService: any;
  let serverGroupsStream: Subject<void>;
  let instancesStream: Subject<void>;

  const props = (includes: Mock) =>
    ({
      application,
      cluster: 'test',
      hasDiscovery: false,
      hasLoadBalancers: false,
      router: { transitionService: { onSuccess: () => () => undefined } },
      serverGroup,
      sortFilter,
      stateParams: {},
      stateService: { includes },
    } as any);

  beforeEach(() => {
    previousMultiselectModel = ClusterState.multiselectModel;
    previousFilterService = ClusterState.filterService;
    serverGroupsStream = new Subject<void>();
    instancesStream = new Subject<void>();
    ClusterState.multiselectModel = {
      instancesStream,
      serverGroupsStream,
      serverGroupIsSelected: vi.fn().mockReturnValue(false),
      toggleServerGroup: vi.fn(),
    } as any;
    ClusterState.filterService = { shouldShowInstance: () => true } as any;
  });

  afterEach(() => {
    ClusterState.multiselectModel = previousMultiselectModel;
    ClusterState.filterService = previousFilterService;
  });

  it('selects a server group through the injected state service', () => {
    const includes = vi.fn().mockReturnValue(true);
    const { container } = render(<ServerGroupComponent {...props(includes)} />);

    expect(container.querySelector('.server-group')).toHaveClass('active');
    expect(includes).toHaveBeenCalledWith('**.serverGroup', {
      accountId: 'test-account',
      provider: 'kubernetes',
      region: 'test-region',
      serverGroup: 'test-v001',
    });
  });

  it('renders server group multiselection from the existing selection stream', () => {
    const serverGroupIsSelected = ClusterState.multiselectModel.serverGroupIsSelected as Mock;
    const selectedServerGroup = {
      ...serverGroup,
      instances: [{ name: 'test-v001-0', buildInfo: { images: [] } }],
    } as any;
    const streamProps = {
      ...props(vi.fn().mockReturnValue(false)),
      serverGroup: selectedServerGroup,
      sortFilter: { ...sortFilter, multiselect: true },
    } as any;
    const { container, unmount } = render(<ServerGroupComponent {...streamProps} />);

    const checkbox = container.querySelector<HTMLInputElement>('input[type="checkbox"]');
    expect(checkbox).not.toBeChecked();
    serverGroupIsSelected.mockReturnValue(true);
    act(() => serverGroupsStream.next());

    expect(checkbox).toBeChecked();

    unmount();
    expect(serverGroupsStream.observers).toHaveLength(0);
  });
});

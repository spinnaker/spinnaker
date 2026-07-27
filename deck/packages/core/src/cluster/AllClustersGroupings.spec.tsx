import { shallow } from 'enzyme';
import React from 'react';
import { Subject } from 'rxjs';

import { AllClustersGroupingsComponent } from './AllClustersGroupings';
import { ClusterState, initialize } from '../state';

describe('AllClustersGroupings', () => {
  beforeEach(() => initialize());

  it('scrolls to the server group identified by the injected route params', () => {
    const groupsUpdatedStream = new Subject<any[]>();
    const scrollToRow = jasmine.createSpy('scrollToRow');
    const component = new AllClustersGroupingsComponent({
      app: {} as any,
      initialized: true,
      router: {} as any,
      stateParams: {
        accountId: 'account',
        region: 'region',
        serverGroup: 'server-group',
      },
      stateService: {} as any,
    } as any);
    component.state = {
      ...component.state,
      groups: [
        {
          subgroups: [
            {
              serverGroups: [{ account: 'account', name: 'server-group', region: 'region' }],
            },
          ],
        } as any,
      ],
    };
    (component as any).clusterFilterService = { groupsUpdatedStream };
    (component as any).listRef = { scrollToRow };

    (component as any).scrollToRow();
    groupsUpdatedStream.next([]);

    expect(scrollToRow).toHaveBeenCalledWith(0);
  });

  it('clears cached row heights after a relevant transition from the injected router', () => {
    let onTransitionSuccess: (transition: any) => void;
    const onSuccess = jasmine
      .createSpy('onSuccess')
      .and.callFake((_criteria: any, callback: (transition: any) => void) => {
        onTransitionSuccess = callback;
        return () => undefined;
      });
    const component = new AllClustersGroupingsComponent({
      app: {} as any,
      initialized: true,
      router: { transitionService: { onSuccess } } as any,
      stateParams: {},
      stateService: {} as any,
    });
    const clearAll = spyOn((component as any).cellCache, 'clearAll');

    component.componentDidMount();
    onTransitionSuccess({
      from: () => ({ name: 'previous' }),
      params: () => ({}),
      to: () => ({ name: 'home.applications.application.insight.clusters.instanceDetails' }),
    });

    expect(clearAll).toHaveBeenCalled();
    component.componentWillUnmount();
  });

  it('updates grouping and row heights through groupsUpdatedStream', () => {
    const groupsUpdatedStream = new Subject<any[]>();
    const sortFilter = { listInstances: false } as any;
    ClusterState.filterModel = { asFilterModel: { groups: [], sortFilter } } as any;
    ClusterState.filterService = { groupsUpdatedStream } as any;
    const onSuccess = jasmine.createSpy('onSuccess').and.returnValue(() => undefined);
    const wrapper = shallow(
      <AllClustersGroupingsComponent
        app={{ getDataSource: () => ({ loadFailure: false, data: [], fetchOnDemand: false }) } as any}
        initialized={false}
        router={{ transitionService: { onSuccess } } as any}
        stateParams={{}}
        stateService={{} as any}
      />,
    );
    const updatedGrouping = { key: 'updated-grouping', subgroups: [] } as any;

    const component = wrapper.instance() as AllClustersGroupingsComponent;
    const recomputeRowHeights = jasmine.createSpy('recomputeRowHeights');
    (component as any).listRef = { recomputeRowHeights };

    groupsUpdatedStream.next([{ subgroups: [updatedGrouping] }]);

    expect(component.state.groups).toEqual([updatedGrouping]);
    expect(recomputeRowHeights).toHaveBeenCalledOnceWith(0);
    wrapper.unmount();
    expect(groupsUpdatedStream.observers.length).toBe(0);
  });
});

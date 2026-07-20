import { ReactInjector } from '@spinnaker/core';

import { TargetGroupDetails } from './TargetGroupDetails';

describe('TargetGroupDetails', () => {
  let $state: any;

  beforeEach(() => {
    $state = { params: {}, go: jasmine.createSpy('go') };
    spyOnProperty(ReactInjector, '$state', 'get').and.returnValue($state);
  });

  function buildComponent(loadBalancers: any[]): TargetGroupDetails {
    const app = {
      getDataSource: () => ({ data: loadBalancers }),
    } as any;

    return new TargetGroupDetails({
      accountId: 'test-account',
      app,
      name: 'test-target-group',
      provider: 'aws',
      targetGroup: {
        accountId: 'test-account',
        loadBalancerName: 'test-load-balancer',
        name: 'test-target-group',
        provider: 'aws',
        region: 'us-west-2',
      },
    });
  }

  it('auto-closes when the load balancer disappears', () => {
    const component = buildComponent([]);

    (component as any).extractTargetGroup();

    expect($state.params.allowModalToStayOpen).toBe(true);
    expect($state.go).toHaveBeenCalledWith('^', null, { location: 'replace' });
  });

  it('auto-closes when the target group disappears', () => {
    const component = buildComponent([
      {
        account: 'test-account',
        name: 'test-load-balancer',
        region: 'us-west-2',
        targetGroups: [],
      },
    ]);

    (component as any).extractTargetGroup();

    expect($state.params.allowModalToStayOpen).toBe(true);
    expect($state.go).toHaveBeenCalledWith('^', null, { location: 'replace' });
  });

  it('does not update state after unmount when the target group still exists', () => {
    const component = buildComponent([
      {
        account: 'test-account',
        name: 'test-load-balancer',
        region: 'us-west-2',
        targetGroups: [{ name: 'test-target-group' }],
      },
    ]);
    spyOn(component, 'setState');

    component.componentWillUnmount();
    (component as any).extractTargetGroup();

    expect(component.setState).not.toHaveBeenCalled();
  });

  it('stops loading when load balancers fail to become ready', () => {
    const readyFailure = new Error('load balancers failed');
    const rejectedReadiness = {
      then: () => rejectedReadiness,
      catch: (onRejected: (error: Error) => void) => {
        onRejected(readyFailure);
        return rejectedReadiness;
      },
    };
    const app = {
      getDataSource: () => ({
        data: [],
        ready: jasmine.createSpy('ready').and.returnValue(rejectedReadiness),
        onRefresh: jasmine.createSpy('onRefresh'),
      }),
    } as any;
    const component = new TargetGroupDetails({
      accountId: 'test-account',
      app,
      name: 'test-target-group',
      provider: 'aws',
      targetGroup: {
        accountId: 'test-account',
        loadBalancerName: 'test-load-balancer',
        name: 'test-target-group',
        provider: 'aws',
        region: 'us-west-2',
      },
    });
    spyOn(component, 'setState');

    component.componentDidMount();

    expect(component.setState).toHaveBeenCalledWith({ loading: false });
  });
});

import { TargetGroupDetailsComponent as TargetGroupDetails } from './TargetGroupDetails';

describe('TargetGroupDetails', () => {
  let stateService: any;

  beforeEach(() => {
    stateService = { params: {}, go: vi.fn() };
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
      router: {},
      stateParams: {},
      stateService,
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

    expect(stateService.params.allowModalToStayOpen).toBe(true);
    expect(stateService.go).toHaveBeenCalledWith('^', null, { location: 'replace' });
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

    expect(stateService.params.allowModalToStayOpen).toBe(true);
    expect(stateService.go).toHaveBeenCalledWith('^', null, { location: 'replace' });
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
    vi.spyOn(component, 'setState').mockReturnValue(undefined);

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
        ready: vi.fn().mockReturnValue(rejectedReadiness),
        onRefresh: vi.fn(),
      }),
    } as any;
    const component = new TargetGroupDetails({
      accountId: 'test-account',
      app,
      name: 'test-target-group',
      provider: 'aws',
      router: {},
      stateParams: {},
      stateService,
      targetGroup: {
        accountId: 'test-account',
        loadBalancerName: 'test-load-balancer',
        name: 'test-target-group',
        provider: 'aws',
        region: 'us-west-2',
      },
    });
    vi.spyOn(component, 'setState').mockReturnValue(undefined);

    component.componentDidMount();

    expect(component.setState).toHaveBeenCalledWith({ loading: false });
  });
});

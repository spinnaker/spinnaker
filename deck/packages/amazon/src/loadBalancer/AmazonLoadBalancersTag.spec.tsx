import { AmazonLoadBalancersTagComponent } from './AmazonLoadBalancersTag';

describe('AmazonLoadBalancersTag', () => {
  it('opens load balancer and target group details through the injected state service', () => {
    const stateService = {
      current: { name: 'home.applications.application.insight.clusters' },
      go: jasmine.createSpy('go'),
    };
    const component = new AmazonLoadBalancersTagComponent({
      application: {},
      router: {},
      serverGroup: { account: 'test', region: 'us-east-1', type: 'aws' },
      stateParams: {},
      stateService,
    } as any);

    (component as any).showLoadBalancerDetails({ name: 'app-lb' });
    (component as any).showTargetGroupDetails({
      account: 'test',
      loadBalancerNames: ['app-lb'],
      name: 'app-target',
      region: 'us-east-1',
    });

    expect(stateService.go).toHaveBeenCalledWith('.loadBalancerDetails', {
      accountId: 'test',
      name: 'app-lb',
      provider: 'aws',
      region: 'us-east-1',
    });
    expect(stateService.go).toHaveBeenCalledWith('.targetGroupDetails', {
      accountId: 'test',
      loadBalancerName: 'app-lb',
      name: 'app-target',
      provider: 'aws',
      region: 'us-east-1',
    });
  });
});

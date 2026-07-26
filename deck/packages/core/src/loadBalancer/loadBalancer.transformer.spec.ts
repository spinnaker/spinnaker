import { createLoadBalancerTransformer } from './loadBalancer.transformer';

describe('createLoadBalancerTransformer', () => {
  const provider = 'loadBalancerTransformerTest';

  it('preserves provider item and set normalization', async () => {
    const normalizeLoadBalancerSet = jasmine
      .createSpy('normalizeLoadBalancerSet')
      .and.callFake((loadBalancers: any[]) => loadBalancers.slice().reverse());
    const providerTransformer = {
      context: provider,
      normalizeLoadBalancer(loadBalancer: any) {
        return Promise.resolve({ ...loadBalancer, context: this.context });
      },
    };
    const providerSetTransformer = { normalizeLoadBalancerSet };
    const providerServiceDelegate = {
      hasDelegate: jasmine.createSpy('hasDelegate').and.callFake((requestedProvider: string, serviceKey: string) => {
        return (
          requestedProvider === provider &&
          ['loadBalancer.transformer', 'loadBalancer.setTransformer'].includes(serviceKey)
        );
      }),
      getDelegate: jasmine.createSpy('getDelegate').and.callFake((_requestedProvider: string, serviceKey: string) => {
        return serviceKey === 'loadBalancer.transformer' ? providerTransformer : providerSetTransformer;
      }),
    };
    const transformer = createLoadBalancerTransformer(providerServiceDelegate);
    const loadBalancers = [
      { name: 'app-test-detail', provider },
      { name: 'app-test-other', provider },
    ];

    await expectAsync(transformer.normalizeLoadBalancer(loadBalancers[0])).toBeResolvedTo(
      jasmine.objectContaining({ context: provider }),
    );
    expect(transformer.normalizeLoadBalancerSet(loadBalancers)).toEqual(loadBalancers.slice().reverse());
    expect(normalizeLoadBalancerSet).toHaveBeenCalledTimes(1);
    expect(providerServiceDelegate.getDelegate).toHaveBeenCalledWith(provider, 'loadBalancer.transformer');
    expect(providerServiceDelegate.getDelegate).toHaveBeenCalledWith(provider, 'loadBalancer.setTransformer');
  });

  it('resolves each provider set transformer once', () => {
    const secondProvider = `${provider}Second`;
    const resolvedProviders: string[] = [];
    const createdDelegates: any[] = [];
    const invokedDelegates: any[] = [];
    const providerServiceDelegate = {
      hasDelegate: jasmine.createSpy('hasDelegate').and.returnValue(true),
      getDelegate: jasmine.createSpy('getDelegate').and.callFake((requestedProvider: string) => {
        resolvedProviders.push(requestedProvider);
        const delegate = {
          normalizeLoadBalancerSet(loadBalancers: any[]) {
            invokedDelegates.push(this);
            return loadBalancers;
          },
        };
        createdDelegates.push(delegate);
        return delegate;
      }),
    };
    const transformer = createLoadBalancerTransformer(providerServiceDelegate);
    const loadBalancers = [
      { name: 'app-test-one', provider },
      { name: 'app-test-two', provider },
      { name: 'app-test-three', provider: secondProvider },
      { name: 'app-test-four', provider },
      { name: 'app-test-five', provider: secondProvider },
    ];

    expect(transformer.normalizeLoadBalancerSet(loadBalancers)).toBe(loadBalancers);
    expect(resolvedProviders).toEqual([provider, secondProvider]);
    expect(providerServiceDelegate.hasDelegate.calls.allArgs()).toEqual([
      [provider, 'loadBalancer.setTransformer'],
      [secondProvider, 'loadBalancer.setTransformer'],
    ]);
    expect(invokedDelegates).toEqual(createdDelegates);
  });

  it('leaves unregistered providers unchanged', async () => {
    const providerServiceDelegate = {
      hasDelegate: jasmine.createSpy('hasDelegate').and.returnValue(false),
      getDelegate: jasmine.createSpy('getDelegate'),
    };
    const transformer = createLoadBalancerTransformer(providerServiceDelegate);
    const loadBalancer = { name: 'app-test-detail', provider };

    await expectAsync(transformer.normalizeLoadBalancer(loadBalancer)).toBeResolvedTo(loadBalancer);
    expect(providerServiceDelegate.getDelegate).not.toHaveBeenCalled();
  });
});

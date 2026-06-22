describe('gceLoadBalancerSetTransformer', () => {
  let transformer;

  beforeEach(window.module(require('./loadBalancer.setTransformer').name));

  beforeEach(() => {
    window.inject((_gceLoadBalancerSetTransformer_) => {
      transformer = _gceLoadBalancerSetTransformer_;
    });
  });

  it('groups HTTP load balancers by account, region, and URL map name', () => {
    const loadBalancers = [
      {
        name: 'listener-a',
        account: 'test',
        provider: 'gce',
        region: 'us-central1',
        loadBalancerType: 'EXTERNAL_MANAGED',
        urlMapName: 'app',
        portRange: '80',
      },
      {
        name: 'listener-b',
        account: 'test',
        provider: 'gce',
        region: 'us-east1',
        loadBalancerType: 'EXTERNAL_MANAGED',
        urlMapName: 'app',
        portRange: '80',
      },
    ];

    const normalized = transformer.normalizeLoadBalancerSet(loadBalancers);

    expect(normalized.length).toBe(2);
    expect(normalized.map((lb) => lb.region).sort()).toEqual(['us-central1', 'us-east1']);
    expect(normalized.map((lb) => lb.name).sort()).toEqual(['app (test/us-central1)', 'app (test/us-east1)']);
  });

  it('folds same-scope regional external listeners into one normalized load balancer', () => {
    const loadBalancers = [
      {
        name: 'listener-http',
        account: 'test',
        provider: 'gce',
        region: 'us-central1',
        loadBalancerType: 'EXTERNAL_MANAGED',
        urlMapName: 'app',
        portRange: '80',
        ipAddress: '34.0.0.1',
        networkTier: 'STANDARD',
      },
      {
        name: 'listener-https',
        account: 'test',
        provider: 'gce',
        region: 'us-central1',
        loadBalancerType: 'EXTERNAL_MANAGED',
        urlMapName: 'app',
        portRange: '443',
        certificate: '//certificatemanager.googleapis.com/projects/p/locations/us-central1/certificates/cert',
        ipAddress: '34.0.0.2',
        networkTier: 'PREMIUM',
      },
    ];

    const [normalized] = transformer.normalizeLoadBalancerSet(loadBalancers);

    expect(normalized.name).toBe('app (test/us-central1)');
    expect(normalized.listeners).toEqual([
      jasmine.objectContaining({
        name: 'listener-http',
        port: '80',
        ipAddress: '34.0.0.1',
        networkTier: 'STANDARD',
      }),
      jasmine.objectContaining({
        name: 'listener-https',
        port: '443',
        certificate: '//certificatemanager.googleapis.com/projects/p/locations/us-central1/certificates/cert',
        ipAddress: '34.0.0.2',
        networkTier: 'PREMIUM',
      }),
    ]);
  });
});

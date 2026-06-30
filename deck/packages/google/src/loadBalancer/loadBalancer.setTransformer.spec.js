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
    expect(normalized.map((lb) => lb.name).sort()).toEqual([
      'app (test/us-central1/EXTERNAL_MANAGED)',
      'app (test/us-east1/EXTERNAL_MANAGED)',
    ]);
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

    expect(normalized.name).toBe('app (test/us-central1/EXTERNAL_MANAGED)');
    expect(normalized.urlMapName).toBe('app');
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

  it('does not fold internal and external managed load balancers that share a URL map', () => {
    const loadBalancers = [
      {
        name: 'internal-listener',
        account: 'test',
        provider: 'gce',
        region: 'us-central1',
        loadBalancerType: 'INTERNAL_MANAGED',
        urlMapName: 'shared-app',
        portRange: '80',
      },
      {
        name: 'external-listener',
        account: 'test',
        provider: 'gce',
        region: 'us-central1',
        loadBalancerType: 'EXTERNAL_MANAGED',
        urlMapName: 'shared-app',
        portRange: '443',
      },
    ];

    const normalized = transformer.normalizeLoadBalancerSet(loadBalancers);

    expect(normalized.length).toBe(2);
    expect(normalized.map((lb) => lb.loadBalancerType).sort()).toEqual(['EXTERNAL_MANAGED', 'INTERNAL_MANAGED']);
    expect(normalized.map((lb) => lb.name).sort()).toEqual([
      'shared-app (test/us-central1/EXTERNAL_MANAGED)',
      'shared-app (test/us-central1/INTERNAL_MANAGED)',
    ]);
    expect(normalized.map((lb) => lb.listeners[0].name).sort()).toEqual(['external-listener', 'internal-listener']);
  });

  it('preserves raw URL map identity for regional internal HTTP load balancers', () => {
    const loadBalancers = [
      {
        name: 'internal-listener',
        account: 'test',
        provider: 'gce',
        region: 'us-central1',
        loadBalancerType: 'INTERNAL_MANAGED',
        urlMapName: 'internal-app',
        portRange: '80',
      },
    ];

    const [normalized] = transformer.normalizeLoadBalancerSet(loadBalancers);

    expect(normalized.name).toBe('internal-app (test/us-central1/INTERNAL_MANAGED)');
    expect(normalized.urlMapName).toBe('internal-app');
    expect(normalized.listeners[0].name).toBe('internal-listener');
  });

  it('leaves regional external network load balancers ungrouped', () => {
    const loadBalancers = [
      {
        name: 'regional-external-network-lb',
        account: 'test',
        provider: 'gce',
        region: 'us-central1',
        loadBalancerType: 'REGIONAL_EXTERNAL_NETWORK',
        ports: ['80', '443'],
      },
    ];

    const normalized = transformer.normalizeLoadBalancerSet(loadBalancers);

    expect(normalized).toEqual(loadBalancers);
  });
});

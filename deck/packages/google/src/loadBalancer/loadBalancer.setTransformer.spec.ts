import { GceLoadBalancerSetTransformer } from './loadBalancer.setTransformer';

describe('GceLoadBalancerSetTransformer', () => {
  const transformer = new GceLoadBalancerSetTransformer();

  it('groups regional HTTP listeners by account, region, load balancer type, and raw urlMapName', () => {
    const loadBalancers = [
      {
        account: 'test',
        loadBalancerType: 'EXTERNAL_MANAGED',
        name: 'listener-a',
        portRange: '80-80',
        provider: 'gce',
        region: 'us-central1',
        urlMapName: 'app',
      },
      {
        account: 'test',
        loadBalancerType: 'EXTERNAL_MANAGED',
        name: 'listener-b',
        portRange: '80-80',
        provider: 'gce',
        region: 'us-east1',
        urlMapName: 'app',
      },
    ];

    const normalized = transformer.normalizeLoadBalancerSet(loadBalancers as any);

    expect(normalized.length).toBe(2);
    expect(normalized.map((loadBalancer) => loadBalancer.region).sort()).toEqual(['us-central1', 'us-east1']);
    expect(normalized.map((loadBalancer) => loadBalancer.name).sort()).toEqual([
      'app (test/us-central1/EXTERNAL_MANAGED)',
      'app (test/us-east1/EXTERNAL_MANAGED)',
    ]);
    expect(normalized.every((loadBalancer: any) => loadBalancer.urlMapName === 'app')).toBe(true);
  });

  it('folds same-scope regional external listeners into one normalized load balancer', () => {
    const loadBalancers = [
      {
        account: 'test',
        certificate: undefined,
        ipAddress: '34.0.0.1',
        loadBalancerType: 'EXTERNAL_MANAGED',
        name: 'listener-http',
        networkTier: 'STANDARD',
        portRange: '80-80',
        provider: 'gce',
        region: 'us-central1',
        urlMapName: 'app',
      },
      {
        account: 'test',
        certificate: '//certificatemanager.googleapis.com/projects/p/locations/us-central1/certificates/cert',
        ipAddress: '34.0.0.2',
        loadBalancerType: 'EXTERNAL_MANAGED',
        name: 'listener-https',
        networkTier: 'PREMIUM',
        portRange: '443-443',
        provider: 'gce',
        region: 'us-central1',
        urlMapName: 'app',
      },
    ];

    const [normalized] = transformer.normalizeLoadBalancerSet(loadBalancers as any);

    expect(normalized.name).toBe('app (test/us-central1/EXTERNAL_MANAGED)');
    expect((normalized as any).urlMapName).toBe('app');
    expect((normalized as any).listeners).toEqual([
      jasmine.objectContaining({
        certificate: undefined,
        ipAddress: '34.0.0.1',
        name: 'listener-http',
        networkTier: 'STANDARD',
        port: '80',
      }),
      jasmine.objectContaining({
        certificate: '//certificatemanager.googleapis.com/projects/p/locations/us-central1/certificates/cert',
        ipAddress: '34.0.0.2',
        name: 'listener-https',
        networkTier: 'PREMIUM',
        port: '443',
      }),
    ]);
  });

  it('keeps global HTTP normalization unchanged', () => {
    const result = transformer.normalizeLoadBalancerSet([
      {
        account: 'test',
        loadBalancerType: 'HTTP',
        name: 'forwarding-rule-80',
        portRange: '80-80',
        provider: 'gce',
        region: 'global',
        urlMapName: 'frontend-map',
      },
      {
        account: 'test',
        loadBalancerType: 'HTTP',
        name: 'forwarding-rule-443',
        portRange: '443-443',
        provider: 'gce',
        region: 'global',
        urlMapName: 'frontend-map',
      },
    ] as any);

    expect(result.length).toBe(1);
    expect(result[0].name).toBe('frontend-map');
    expect((result[0] as any).listeners.map((listener: any) => listener.port)).toEqual(['80', '443']);
  });

  it('does not group REGIONAL_EXTERNAL_NETWORK load balancers with regional HTTP families', () => {
    const networkLoadBalancer = {
      account: 'test',
      loadBalancerType: 'REGIONAL_EXTERNAL_NETWORK',
      name: 'regional-network-lb',
      provider: 'gce',
      region: 'us-central1',
    };
    const httpListener = {
      account: 'test',
      loadBalancerType: 'EXTERNAL_MANAGED',
      name: 'listener-http',
      portRange: '80-80',
      provider: 'gce',
      region: 'us-central1',
      urlMapName: 'app',
    };

    const normalized = transformer.normalizeLoadBalancerSet([httpListener, networkLoadBalancer] as any);

    expect(normalized.length).toBe(2);
    expect(normalized.find((loadBalancer) => loadBalancer.name === 'regional-network-lb')).toBe(networkLoadBalancer);
  });
});

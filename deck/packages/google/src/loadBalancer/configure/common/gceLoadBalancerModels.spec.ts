import {
  GCE_LOAD_BALANCER_CAPABILITIES,
  GCE_LOAD_BALANCER_TYPES,
  normalizeGceLoadBalancerCommand,
  serializeGceLoadBalancerCommand,
} from './gceLoadBalancerModels';

describe('GCE load balancer models', () => {
  it('defines capabilities for every supported load balancer type', () => {
    expect(GCE_LOAD_BALANCER_TYPES).toEqual(['NETWORK', 'INTERNAL', 'TCP', 'SSL', 'HTTP', 'INTERNAL_MANAGED']);
    expect(Object.keys(GCE_LOAD_BALANCER_CAPABILITIES)).toEqual(GCE_LOAD_BALANCER_TYPES);
    expect(GCE_LOAD_BALANCER_CAPABILITIES.NETWORK).toEqual({
      address: true,
      backendServices: false,
      certificates: false,
      healthChecks: true,
      hostRules: false,
      network: false,
      subnet: false,
    });
    expect(GCE_LOAD_BALANCER_CAPABILITIES.INTERNAL_MANAGED).toEqual({
      address: true,
      backendServices: true,
      certificates: true,
      healthChecks: true,
      hostRules: true,
      network: true,
      subnet: true,
    });
  });

  it('normalizes persisted edit data without losing unknown resource references', () => {
    const command = normalizeGceLoadBalancerCommand(
      {
        account: 'test-account',
        backendServices: [
          {
            healthCheck: 'https://compute.googleapis.com/projects/test/global/healthChecks/removed-check',
            name: 'removed-backend',
          },
        ],
        certificate: 'https://compute.googleapis.com/projects/test/global/sslCertificates/removed-cert',
        ipAddress: { address: '1.2.3.4', name: 'removed-address', custom: 'preserve-me' },
        loadBalancerType: 'ssl',
        name: 'app-main',
        portRange: 443,
        region: 'global',
      },
      'edit',
    );

    expect(command.mode).toBe('edit');
    expect(command.credentials).toBe('test-account');
    expect(command.loadBalancerType).toBe('SSL');
    expect(command.listeners).toEqual([
      {
        address: { address: '1.2.3.4', custom: 'preserve-me', name: 'removed-address' },
        certificate: {
          name: 'removed-cert',
          selfLink: 'https://compute.googleapis.com/projects/test/global/sslCertificates/removed-cert',
        },
        name: 'app-main',
        portRange: '443',
        protocol: 'SSL',
      },
    ]);
    expect(command.backendServices[0].healthCheck).toEqual({
      name: 'removed-check',
      selfLink: 'https://compute.googleapis.com/projects/test/global/healthChecks/removed-check',
    });
  });

  it('normalizes listener, health-check, backend, host, and path models', () => {
    const command = normalizeGceLoadBalancerCommand(
      {
        account: 'test-account',
        defaultService: 'default-backend',
        healthChecks: [{ healthCheckType: 'http', name: 'web-check', port: '8080', requestPath: 'health' }],
        hostRules: [
          {
            hostPatterns: 'api.example.com',
            pathMatcher: {
              defaultService: 'default-backend',
              pathRules: [{ backendService: 'api-backend', paths: '/v1' }],
            },
          },
        ],
        listeners: [{ ipAddress: 'frontend-address', name: 'frontend', port: 80, protocol: 'http' }],
        loadBalancerType: 'http',
        name: 'web',
      },
      'pipeline',
    );

    expect(command.mode).toBe('pipeline');
    expect(command.listeners).toEqual([
      {
        address: { name: 'frontend-address' },
        name: 'frontend',
        portRange: '80',
        protocol: 'HTTP',
      },
    ]);
    expect(command.healthChecks).toEqual([
      { healthCheckType: 'HTTP', name: 'web-check', port: 8080, requestPath: '/health' },
    ]);
    expect(command.defaultService).toEqual({ name: 'default-backend' });
    expect(command.hostRules).toEqual([
      {
        hostPatterns: ['api.example.com'],
        pathMatcher: {
          defaultService: { name: 'default-backend' },
          pathRules: [{ backendService: { name: 'api-backend' }, paths: ['/v1'] }],
        },
      },
    ]);
  });

  (['HTTP', 'INTERNAL_MANAGED'] as const).forEach((loadBalancerType) => {
    it(`serializes ${loadBalancerType} HTTPS listeners on port 443`, () => {
      const command = normalizeGceLoadBalancerCommand(
        {
          account: 'account-a',
          listeners: [{ certificate: 'cert-a', name: 'frontend', port: 8443, protocol: 'HTTPS' }],
          loadBalancerType,
          name: 'web',
          region: loadBalancerType === 'INTERNAL_MANAGED' ? 'europe-west1' : 'global',
        },
        'pipeline',
      );

      expect((serializeGceLoadBalancerCommand(command).listeners as any[])[0].portRange).toBe('443');
    });
  });

  it('serializes only fields supported by the selected load balancer type', () => {
    const input = {
      account: 'test-account',
      backendServices: [{ name: 'backend' }],
      certificate: 'certificate',
      healthChecks: [{ name: 'check' }],
      hostRules: [{ hostPatterns: ['example.com'], pathMatcher: { pathRules: [] } }],
      ipAddress: 'address',
      loadBalancerType: 'network',
      name: 'network-lb',
      network: 'network',
      portRange: '80',
      region: 'europe-west1',
      subnet: 'subnet',
    };

    const serialized = serializeGceLoadBalancerCommand(normalizeGceLoadBalancerCommand(input, 'create'));

    expect(serialized).toEqual({
      cloudProvider: 'gce',
      credentials: 'test-account',
      healthChecks: [{ name: 'check' }],
      ipAddress: 'address',
      ipProtocol: 'TCP',
      loadBalancerName: 'network-lb',
      loadBalancerType: 'NETWORK',
      name: 'network-lb',
      portRange: '80',
      provider: 'gce',
      region: 'europe-west1',
      type: 'upsertLoadBalancer',
    });
    expect(serialized.backendServices).toBeUndefined();
    expect(serialized.certificate).toBeUndefined();
    expect(serialized.hostRules).toBeUndefined();
    expect(serialized.network).toBeUndefined();
    expect(serialized.subnet).toBeUndefined();
  });

  it('captures a detached edit snapshot from nested persisted backend contracts', () => {
    const command = normalizeGceLoadBalancerCommand(
      {
        account: 'account-a',
        defaultService: {
          backends: [{ serverGroupUrl: 'projects/test/zones/europe-west1-b/instanceGroups/default-group' }],
          healthCheck: { healthCheckType: 'HTTP', name: 'default-check', port: 80 },
          name: 'default-backend',
        },
        hostRules: [
          {
            hostPatterns: ['api.example.com'],
            pathMatcher: {
              defaultService: {
                backends: [],
                healthCheck: { healthCheckType: 'HTTP', name: 'fallback-check', port: 8080 },
                name: 'fallback-backend',
              },
              pathRules: [
                {
                  backendService: {
                    backends: [{ serverGroupUrl: 'projects/test/regions/europe-west1/instanceGroups/api-group' }],
                    healthCheck: { healthCheckType: 'HTTPS', name: 'api-check', port: 443 },
                    name: 'api-backend',
                  },
                  paths: ['/api'],
                },
              ],
            },
          },
        ],
        listeners: [
          { name: 'app-http', port: 80 },
          { certificate: 'app-cert', name: 'app-https', port: 443 },
        ],
        loadBalancerName: 'app-http',
        loadBalancerType: 'HTTP',
        urlMapName: 'app-main',
      },
      'edit',
    );

    expect(command.name).toBe('app-main');
    expect(command.backendServices.map(({ name }) => name)).toEqual([
      'default-backend',
      'fallback-backend',
      'api-backend',
    ]);
    expect(command.healthChecks.map(({ name }) => name)).toEqual(['default-check', 'fallback-check', 'api-check']);
    expect(command.listeners.map(({ protocol }) => protocol)).toEqual(['HTTP', 'HTTPS']);
    expect(command.original).toEqual({
      backendServices: command.backendServices,
      healthChecks: command.healthChecks,
      listeners: command.listeners,
    });

    command.listeners.pop();
    command.backendServices[2].backends = [];

    expect(command.original?.listeners.map(({ name }) => name)).toEqual(['app-http', 'app-https']);
    expect(command.original?.backendServices[2].backends).toEqual([
      { serverGroupUrl: 'projects/test/regions/europe-west1/instanceGroups/api-group' },
    ]);
    expect(command.original?.healthChecks.map(({ name }) => name)).toEqual([
      'default-check',
      'fallback-check',
      'api-check',
    ]);
  });

  it('preserves listener and composite identities from a flat HTTP operation', () => {
    const command = normalizeGceLoadBalancerCommand(
      {
        credentials: 'account-a',
        ipAddress: 'address-a',
        ipProtocol: 'TCP',
        loadBalancerName: 'app-http',
        loadBalancerType: 'HTTP',
        name: 'app-http',
        portRange: '80',
        region: 'global',
        urlMapName: 'app-main',
      },
      'pipeline',
    );

    expect(command.name).toBe('app-main');
    expect(command.listeners).toEqual([
      {
        address: { name: 'address-a' },
        name: 'app-http',
        portRange: '80',
        protocol: 'HTTP',
      },
    ]);
  });

  (['NETWORK', 'INTERNAL', 'TCP', 'SSL'] as const).forEach((loadBalancerType) => {
    it(`serializes ${loadBalancerType} reader resources using field-specific backend identities`, () => {
      const command = normalizeGceLoadBalancerCommand(
        {
          account: 'account-a',
          backendServices: [
            {
              healthCheck: {
                name: 'check-a',
                selfLink: 'projects/test/global/healthChecks/check-a',
              },
              name: 'backend-a',
            },
          ],
          certificate: {
            account: 'account-a',
            name: 'cert-a',
            provider: 'gce',
            selfLink: 'projects/test/global/sslCertificates/cert-a',
            type: 'sslCertificates',
          },
          healthChecks: [readerHealthCheck()],
          ipAddress: {
            account: 'account-a',
            address: '203.0.113.10',
            name: 'reserved-address',
            region: loadBalancerType === 'INTERNAL' || loadBalancerType === 'NETWORK' ? 'europe-west1' : 'global',
            selfLink: 'projects/test/global/addresses/reserved-address',
          },
          ipProtocol: 'TCP',
          loadBalancerType,
          name: 'app-main',
          network: { name: 'network-a', selfLink: 'projects/test/global/networks/network-a' },
          portRange: '443',
          region: loadBalancerType === 'INTERNAL' || loadBalancerType === 'NETWORK' ? 'europe-west1' : 'global',
          subnet: {
            name: 'subnet-a',
            selfLink: 'projects/test/regions/europe-west1/subnetworks/subnet-a',
          },
        },
        'pipeline',
      );

      const serialized = serializeGceLoadBalancerCommand(command);

      expect(serialized.ipAddress).toBe('203.0.113.10');
      if (loadBalancerType === 'SSL') {
        expect(serialized.certificate).toBe('cert-a');
      }
      if (loadBalancerType === 'INTERNAL') {
        expect(serialized.network).toBe('network-a');
        expect(serialized.subnet).toBe('subnet-a');
      }
      if (loadBalancerType !== 'NETWORK') {
        expect((serialized.backendServices as any[])[0].healthCheck).toBe('check-a');
      }
    });
  });

  it('normalizes all shared Clouddriver health-check command fields for HTTP', () => {
    const command = normalizeGceLoadBalancerCommand(
      {
        account: 'account-a',
        healthChecks: [readerHealthCheck()],
        loadBalancerType: 'HTTP',
        name: 'app-main',
      },
      'pipeline',
    );

    expect(command.healthChecks).toEqual([
      jasmine.objectContaining({
        checkIntervalSec: 15,
        healthCheckType: 'HTTP',
        healthyThreshold: 2,
        host: 'api.internal',
        name: 'check-a',
        port: 8080,
        proxyHeader: 'PROXY_V1',
        requestPath: '/ready',
        timeoutSec: 5,
        unhealthyThreshold: 3,
        useServingPort: false,
      }),
    ]);
  });

  it('preserves an unnamed inline health check from persisted load-balancer details', () => {
    const command = normalizeGceLoadBalancerCommand(
      {
        healthCheck: { healthCheckType: 'HTTP', port: '53', requestPath: 'health' },
        loadBalancerType: 'NETWORK',
        name: 'app-main',
      },
      'edit',
    );

    expect(command.healthChecks).toEqual([{ healthCheckType: 'HTTP', port: 53, requestPath: '/health' }]);
  });
});

function readerHealthCheck() {
  return {
    account: 'account-a',
    checkIntervalSec: '15',
    healthCheckType: 'http',
    healthyThreshold: '2',
    host: 'api.internal',
    kind: 'healthCheck',
    name: 'check-a',
    port: '8080',
    proxyHeader: 'PROXY_V1',
    requestPath: 'ready',
    selfLink: 'projects/test/global/healthChecks/check-a',
    timeoutSec: '5',
    unhealthyThreshold: '3',
    useServingPort: false,
  };
}

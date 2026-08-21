import { normalizeGceLoadBalancerCommand } from './gceLoadBalancerModels';
import { buildGceLoadBalancerJobs, submitGceLoadBalancerCommand } from './gceLoadBalancerSubmission';

describe('GCE load balancer submission', () => {
  it('builds directly executable HTTP jobs for every listener with complete backend objects', () => {
    const command = normalizeGceLoadBalancerCommand(
      {
        account: 'account-a',
        backendServices: [
          {
            backends: [{ serverGroupUrl: 'projects/test/zones/europe-west1-b/instanceGroups/default-group' }],
            enableCDN: false,
            healthCheck: 'default-check',
            name: 'default-backend',
            portName: 'http',
            sessionAffinity: 'NONE',
          },
          {
            backends: [],
            healthCheck: 'api-check',
            name: 'api-backend',
            portName: 'api',
            sessionAffinity: 'CLIENT_IP',
          },
        ],
        defaultService: 'default-backend',
        healthChecks: [
          {
            checkIntervalSec: 10,
            healthCheckType: 'HTTP',
            healthyThreshold: 2,
            name: 'default-check',
            port: 80,
            requestPath: '/health',
            timeoutSec: 5,
            unhealthyThreshold: 3,
          },
          {
            checkIntervalSec: 15,
            healthCheckType: 'HTTPS',
            healthyThreshold: 2,
            name: 'api-check',
            port: 443,
            requestPath: '/ready',
            timeoutSec: 5,
            unhealthyThreshold: 3,
          },
        ],
        hostRules: [
          {
            hostPatterns: ['api.example.com', 'api.internal.example.com'],
            pathMatcher: {
              defaultService: 'default-backend',
              pathRules: [{ backendService: 'api-backend', paths: ['/v1', '/v2'] }],
            },
          },
        ],
        listeners: [
          { ipAddress: '203.0.113.10', name: 'app-http', port: 80, protocol: 'HTTP' },
          { certificate: 'app-cert', ipAddress: '203.0.113.10', name: 'app-https', port: 443, protocol: 'HTTPS' },
        ],
        loadBalancerType: 'HTTP',
        name: 'app-main',
      },
      'create',
    );

    const jobs = buildGceLoadBalancerJobs(command);

    const defaultHealthCheck = {
      checkIntervalSec: 10,
      healthCheckType: 'HTTP',
      healthyThreshold: 2,
      name: 'default-check',
      port: 80,
      requestPath: '/health',
      timeoutSec: 5,
      unhealthyThreshold: 3,
    };
    const apiHealthCheck = {
      checkIntervalSec: 15,
      healthCheckType: 'HTTPS',
      healthyThreshold: 2,
      name: 'api-check',
      port: 443,
      requestPath: '/ready',
      timeoutSec: 5,
      unhealthyThreshold: 3,
    };
    const defaultService = {
      backends: [{ serverGroupUrl: 'projects/test/zones/europe-west1-b/instanceGroups/default-group' }],
      enableCDN: false,
      healthCheck: defaultHealthCheck,
      name: 'default-backend',
      portName: 'http',
      sessionAffinity: 'NONE',
    };
    const apiService = {
      backends: [],
      healthCheck: apiHealthCheck,
      name: 'api-backend',
      portName: 'api',
      sessionAffinity: 'CLIENT_IP',
    };
    const common = {
      cloudProvider: 'gce',
      credentials: 'account-a',
      defaultService,
      hostRules: [
        {
          hostPatterns: ['api.example.com'],
          pathMatcher: {
            defaultService,
            pathRules: [{ backendService: apiService, paths: ['/v1', '/v2'] }],
          },
        },
        {
          hostPatterns: ['api.internal.example.com'],
          pathMatcher: {
            defaultService,
            pathRules: [{ backendService: apiService, paths: ['/v1', '/v2'] }],
          },
        },
      ],
      ipAddress: '203.0.113.10',
      ipProtocol: 'TCP',
      loadBalancerType: 'HTTP',
      provider: 'gce',
      region: 'global',
      type: 'upsertLoadBalancer',
      urlMapName: 'app-main',
    };
    expect(jobs).toEqual([
      {
        ...common,
        certificate: null,
        loadBalancerName: 'app-http',
        name: 'app-http',
        portRange: '80',
      },
      {
        ...common,
        certificate: 'app-cert',
        loadBalancerName: 'app-https',
        name: 'app-https',
        portRange: '443',
      },
    ]);
  });

  it('computes listener removals and the original backend service diff for edits', () => {
    const command = normalizeGceLoadBalancerCommand(
      {
        account: 'account-a',
        defaultService: {
          backends: [],
          healthCheck: 'default-check',
          name: 'default-backend',
        },
        healthChecks: [
          { healthCheckType: 'HTTP', name: 'default-check', port: 80 },
          { healthCheckType: 'HTTP', name: 'removed-check', port: 8080 },
        ],
        hostRules: [
          {
            hostPatterns: ['old.example.com'],
            pathMatcher: {
              defaultService: {
                backends: [],
                healthCheck: 'default-check',
                name: 'default-backend',
              },
              pathRules: [
                {
                  backendService: {
                    backends: [{ serverGroupUrl: 'projects/test/zones/europe-west1-b/instanceGroups/removed-group' }],
                    healthCheck: 'removed-check',
                    name: 'removed-backend',
                  },
                  paths: ['/old'],
                },
              ],
            },
          },
        ],
        listeners: [
          { name: 'app-http', port: 80 },
          { certificate: 'app-cert', name: 'app-https', port: 443 },
        ],
        loadBalancerType: 'HTTP',
        urlMapName: 'app-main',
      },
      'edit',
    );
    command.listeners = command.listeners.filter(({ name }) => name === 'app-http');
    command.backendServices = command.backendServices.filter(({ name }) => name === 'default-backend');
    command.healthChecks = command.healthChecks.filter(({ name }) => name === 'default-check');
    command.hostRules = [];

    const [job] = buildGceLoadBalancerJobs(command);

    expect(job.listenersToDelete).toEqual(['app-https']);
    expect(job.backendServiceDiff).toEqual([
      {
        backends: [{ serverGroupUrl: 'projects/test/zones/europe-west1-b/instanceGroups/removed-group' }],
        healthCheck: { healthCheckType: 'HTTP', name: 'removed-check', port: 8080 },
        name: 'removed-backend',
      },
    ]);
  });

  it('returns INTERNAL_MANAGED operation commands to Orca in pipeline mode without executing a task', () => {
    const command = normalizeGceLoadBalancerCommand(
      {
        account: 'account-a',
        backendServices: [
          {
            backends: [],
            healthCheck: 'default-check',
            name: 'default-backend',
          },
        ],
        defaultService: 'default-backend',
        healthChecks: [{ healthCheckType: 'HTTP', name: 'default-check', port: 80 }],
        listeners: [
          { ipAddress: '10.0.0.10', name: 'internal-http', port: 80, protocol: 'HTTP', subnet: 'subnet-a' },
          { ipAddress: '10.0.0.11', name: 'internal-api', port: 8080, protocol: 'HTTP', subnet: 'subnet-a' },
        ],
        loadBalancerType: 'INTERNAL_MANAGED',
        name: 'app-internal',
        network: 'network-a',
        region: 'europe-west1',
        subnet: 'subnet-a',
      },
      'pipeline',
    );
    const executeTask = jasmine.createSpy('executeTask');

    const result = submitGceLoadBalancerCommand(command, { application: {} as any, executeTask });

    expect(result).toEqual(buildGceLoadBalancerJobs(command));
    expect((result as any[]).map(({ loadBalancerName }) => loadBalancerName)).toEqual([
      'internal-http',
      'internal-api',
    ]);
    expect((result as any[])[0]).toEqual(
      jasmine.objectContaining({
        ipProtocol: 'TCP',
        loadBalancerName: 'internal-http',
        network: 'network-a',
        portRange: '80',
        region: 'europe-west1',
        subnet: 'subnet-a',
        urlMapName: 'app-internal',
      }),
    );
    expect((result as any[])[0].listeners).toBeUndefined();
    expect((result as any[])[0].backendServices).toBeUndefined();
    expect((result as any[])[0].healthChecks).toBeUndefined();
    expect(executeTask).not.toHaveBeenCalled();
  });

  it('builds exact EXTERNAL_MANAGED create jobs with regional network, tier, and certificate URL', () => {
    const regionalCertUrl =
      '//certificatemanager.googleapis.com/projects/test/locations/europe-west1/certificates/regional-cert';
    const command = normalizeGceLoadBalancerCommand(
      {
        account: 'account-a',
        backendServices: [
          {
            backends: [],
            healthCheck: 'check-a',
            name: 'backend-a',
            portName: 'http',
            sessionAffinity: 'NONE',
          },
        ],
        defaultService: 'backend-a',
        healthChecks: [
          {
            checkIntervalSec: 10,
            healthCheckType: 'HTTP',
            healthyThreshold: 2,
            name: 'check-a',
            port: 80,
            requestPath: '/health',
            timeoutSec: 5,
            unhealthyThreshold: 3,
          },
        ],
        listeners: [
          {
            certificate: regionalCertUrl,
            ipAddress: '203.0.113.10',
            name: 'app-https',
            networkTier: 'STANDARD',
            port: 443,
            protocol: 'HTTPS',
          },
        ],
        loadBalancerType: 'EXTERNAL_MANAGED',
        name: 'app-main',
        network: 'network-a',
        region: 'europe-west1',
      },
      'create',
    );

    const jobs = buildGceLoadBalancerJobs(command);
    const backendService = {
      backends: [],
      healthCheck: {
        checkIntervalSec: 10,
        healthCheckType: 'HTTP',
        healthyThreshold: 2,
        name: 'check-a',
        port: 80,
        requestPath: '/health',
        timeoutSec: 5,
        unhealthyThreshold: 3,
      },
      name: 'backend-a',
      portName: 'http',
      sessionAffinity: 'NONE',
    };

    expect(jobs).toEqual([
      {
        cloudProvider: 'gce',
        credentials: 'account-a',
        defaultService: backendService,
        hostRules: [],
        ipAddress: '203.0.113.10',
        ipProtocol: 'TCP',
        loadBalancerName: 'app-https',
        loadBalancerType: 'EXTERNAL_MANAGED',
        name: 'app-https',
        network: 'network-a',
        networkTier: 'STANDARD',
        portRange: '443',
        provider: 'gce',
        region: 'europe-west1',
        type: 'upsertLoadBalancer',
        urlMapName: 'app-main',
        certificate: regionalCertUrl,
      },
    ]);
    expect((jobs[0] as any).certificateMap).toBeUndefined();
    expect(jobs[0].listeners).toBeUndefined();
    expect(jobs[0].backendServices).toBeUndefined();
    expect(jobs[0].healthChecks).toBeUndefined();
  });

  it('computes EXTERNAL_MANAGED listener deletions and backend diffs for edits', () => {
    const command = normalizeGceLoadBalancerCommand(
      {
        account: 'account-a',
        defaultService: {
          backends: [],
          healthCheck: 'default-check',
          name: 'default-backend',
        },
        healthChecks: [
          { healthCheckType: 'HTTP', name: 'default-check', port: 80 },
          { healthCheckType: 'HTTP', name: 'removed-check', port: 8080 },
        ],
        listeners: [
          { name: 'app-http', port: 80, protocol: 'HTTP' },
          {
            certificate: 'regional-cert',
            name: 'app-https',
            networkTier: 'STANDARD',
            port: 443,
            protocol: 'HTTPS',
          },
        ],
        loadBalancerType: 'EXTERNAL_MANAGED',
        name: 'app-main',
        network: 'network-a',
        region: 'europe-west1',
        urlMapName: 'app-main',
      },
      'edit',
    );
    command.listeners = command.listeners.filter(({ name }) => name === 'app-http');
    command.backendServices = command.backendServices.filter(({ name }) => name === 'default-backend');
    command.healthChecks = command.healthChecks.filter(({ name }) => name === 'default-check');

    const [job] = buildGceLoadBalancerJobs(command);

    expect(job.listenersToDelete).toEqual(['app-https']);
    expect(job.backendServiceDiff).toEqual([]);
    expect(job).toEqual(
      jasmine.objectContaining({
        loadBalancerName: 'app-http',
        loadBalancerType: 'EXTERNAL_MANAGED',
        network: 'network-a',
        networkTier: undefined,
        region: 'europe-west1',
        urlMapName: 'app-main',
      }),
    );
  });

  it('returns EXTERNAL_MANAGED pipeline operations without executing a task', () => {
    const command = normalizeGceLoadBalancerCommand(
      {
        account: 'account-a',
        backendServices: [{ healthCheck: 'check-a', name: 'backend-a', portName: 'http' }],
        defaultService: 'backend-a',
        healthChecks: [{ healthCheckType: 'HTTP', name: 'check-a', port: 80, requestPath: '/health' }],
        listeners: [
          {
            ipAddress: '203.0.113.10',
            name: 'external-http',
            networkTier: 'STANDARD',
            port: 80,
            protocol: 'HTTP',
          },
        ],
        loadBalancerType: 'EXTERNAL_MANAGED',
        name: 'app-external',
        network: 'network-a',
        region: 'europe-west1',
      },
      'pipeline',
    );
    const executeTask = jasmine.createSpy('executeTask');

    const result = submitGceLoadBalancerCommand(command, { application: {} as any, executeTask });

    expect(result).toEqual(buildGceLoadBalancerJobs(command));
    expect((result as any[])[0]).toEqual(
      jasmine.objectContaining({
        ipAddress: '203.0.113.10',
        loadBalancerName: 'external-http',
        loadBalancerType: 'EXTERNAL_MANAGED',
        network: 'network-a',
        networkTier: 'STANDARD',
        portRange: '80',
        region: 'europe-west1',
        urlMapName: 'app-external',
      }),
    );
    expect(executeTask).not.toHaveBeenCalled();
  });

  it('serializes the exact REGIONAL_EXTERNAL_NETWORK Clouddriver contract', () => {
    const command = normalizeGceLoadBalancerCommand(
      {
        account: 'account-a',
        backendService: {
          healthCheck: { healthCheckType: 'TCP', name: 'tcp-check', port: 80 },
          name: 'app-main',
          sessionAffinity: 'CLIENT_IP',
        },
        ipAddress: '35.1.2.3',
        ipProtocol: 'TCP',
        loadBalancerName: 'app-main',
        loadBalancerType: 'REGIONAL_EXTERNAL_NETWORK',
        networkTier: 'PREMIUM',
        ports: ['80', '443'],
        region: 'europe-west1',
      },
      'pipeline',
    );

    const [job] = buildGceLoadBalancerJobs(command);

    expect(job).toEqual({
      backendService: {
        healthCheck: { healthCheckType: 'TCP', name: 'tcp-check', port: 80 },
        name: 'app-main',
        sessionAffinity: 'CLIENT_IP',
      },
      cloudProvider: 'gce',
      credentials: 'account-a',
      ipAddress: '35.1.2.3',
      ipProtocol: 'TCP',
      loadBalancerName: 'app-main',
      loadBalancerType: 'REGIONAL_EXTERNAL_NETWORK',
      name: 'app-main',
      networkTier: 'PREMIUM',
      ports: ['80', '443'],
      provider: 'gce',
      region: 'europe-west1',
      type: 'upsertLoadBalancer',
    });
    expect((job as any).portRange).toBeUndefined();
    expect((job as any).backendServices).toBeUndefined();
    expect((job as any).healthChecks).toBeUndefined();
  });

  it('preserves REGIONAL_EXTERNAL_NETWORK ipAddress and networkTier during edit serialization', () => {
    const command = normalizeGceLoadBalancerCommand(
      {
        account: 'account-a',
        backendService: {
          healthCheck: { healthCheckType: 'UDP', name: 'udp-check', port: 53 },
          name: 'app-main',
          sessionAffinity: 'CLIENT_IP_PROTO',
        },
        ipAddress: '35.1.2.3',
        ipProtocol: 'UDP',
        loadBalancerName: 'app-main',
        loadBalancerType: 'REGIONAL_EXTERNAL_NETWORK',
        networkTier: 'PREMIUM',
        ports: ['53'],
        region: 'europe-west1',
      },
      'edit',
    );
    command.listeners[0].protocol = 'UDP';
    command.listeners[0].address = undefined;
    command.backendServices[0].sessionAffinity = 'CLIENT_IP_PROTO';
    command.networkTier = undefined;

    const [job] = buildGceLoadBalancerJobs(command);

    expect(job.ipAddress).toBe('35.1.2.3');
    expect(job.networkTier).toBe('PREMIUM');
    expect(job.ipProtocol).toBe('UDP');
    expect(job.ports).toEqual(['53']);
  });

  (['HTTP', 'INTERNAL_MANAGED'] as const).forEach((loadBalancerType) => {
    it(`builds ${loadBalancerType} jobs without networkTier`, () => {
      const command = normalizeGceLoadBalancerCommand(
        {
          account: 'account-a',
          backendServices: [{ healthCheck: 'check-a', name: 'backend-a', portName: 'http' }],
          defaultService: 'backend-a',
          healthChecks: [{ healthCheckType: 'HTTP', name: 'check-a', port: 80, requestPath: '/health' }],
          listeners: [
            {
              ipAddress: '203.0.113.10',
              name: 'frontend',
              networkTier: 'STANDARD',
              port: 80,
              protocol: 'HTTP',
            },
          ],
          loadBalancerType,
          name: 'web',
          network: loadBalancerType === 'INTERNAL_MANAGED' ? 'network-a' : undefined,
          region: loadBalancerType === 'INTERNAL_MANAGED' ? 'europe-west1' : 'global',
          subnet: loadBalancerType === 'INTERNAL_MANAGED' ? 'subnet-a' : undefined,
        },
        'create',
      );

      const [job] = buildGceLoadBalancerJobs(command);
      expect(job.networkTier).toBeUndefined();
    });
  });

  it('round-trips a flat HTTP pipeline operation without nesting or changing its identities', () => {
    const operation = {
      cloudProvider: 'gce',
      credentials: 'account-a',
      defaultService: {
        backends: [],
        healthCheck: { healthCheckType: 'HTTP', name: 'default-check', port: 80 },
        name: 'default-backend',
      },
      hostRules: [],
      ipAddress: 'address-a',
      ipProtocol: 'TCP',
      loadBalancerName: 'app-http',
      loadBalancerType: 'HTTP',
      name: 'app-http',
      portRange: '80',
      provider: 'gce',
      region: 'global',
      type: 'upsertLoadBalancer',
      urlMapName: 'app-main',
    };
    const command = normalizeGceLoadBalancerCommand(operation, 'pipeline');

    const result = submitGceLoadBalancerCommand(command, { application: {} as any });

    expect(result).toEqual([
      {
        ...operation,
        certificate: null,
      },
    ]);
    expect(Array.isArray(result)).toBe(true);
    expect((result as unknown[]).every((item) => !Array.isArray(item))).toBe(true);
  });
});

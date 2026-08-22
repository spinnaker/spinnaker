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

import { mock } from 'angular';

describe('Service: gceHttpLoadBalancerTransformer', () => {
  let gceHttpLoadBalancerTransformer;

  beforeEach(mock.module(require('./transformer.service').name));

  beforeEach(
    mock.inject((_gceHttpLoadBalancerTransformer_) => {
      gceHttpLoadBalancerTransformer = _gceHttpLoadBalancerTransformer_;
    }),
  );

  function buildBaseCommand(listener) {
    return {
      loadBalancer: {
        listeners: [listener],
        hostRules: [],
        backendServices: [],
        healthChecks: [],
        defaultService: 'default-service',
      },
      backingData: {
        healthChecksKeyedByName: {
          'hc-1': { name: 'hc-1' },
        },
        backendServicesKeyedByName: {
          'default-service': {
            name: 'default-service',
            sessionAffinity: 'NONE',
            healthCheck: 'hc-1',
          },
        },
      },
    };
  }

  it('serializes certificateMap listeners as certificateMap-only commands', () => {
    const command = buildBaseCommand({
      name: 'app-main',
      port: 443,
      certificate: null,
      certificateMap: 'cm',
      certificateSource: 'certificateMap',
      ipAddress: '10.0.0.1',
      subnet: null,
    });

    const commands = gceHttpLoadBalancerTransformer.serialize(command, null);

    expect(commands.length).toBe(1);
    expect(commands[0].certificate).toBeNull();
    expect(commands[0].certificateMap).toBe('cm');
  });

  it('serializes legacy certificate listeners without certificateMap', () => {
    const command = buildBaseCommand({
      name: 'app-main',
      port: 443,
      certificate: 'legacy-cert',
      certificateMap: 'cm',
      certificateSource: 'certificate',
      ipAddress: '10.0.0.1',
      subnet: null,
    });

    const commands = gceHttpLoadBalancerTransformer.serialize(command, null);

    expect(commands.length).toBe(1);
    expect(commands[0].certificate).toBe('legacy-cert');
    expect(commands[0].certificateMap).toBeNull();
  });

  it('deserializes certificateMap listeners with certificateMap mode', () => {
    const loadBalancer = {
      defaultService: {
        name: 'default-service',
        healthCheck: { name: 'hc-1' },
        sessionAffinity: 'NONE',
      },
      hostRules: [],
      listeners: [
        {
          name: 'app-main',
          certificate: null,
          certificateMap: '//certificatemanager.googleapis.com/projects/p/locations/global/certificateMaps/cm',
        },
      ],
      network: 'default',
      region: 'global',
      urlMapName: 'app',
      account: 'test-account',
    };

    const commandLoadBalancer = gceHttpLoadBalancerTransformer.deserialize(loadBalancer);

    expect(commandLoadBalancer.listeners.length).toBe(1);
    expect(commandLoadBalancer.listeners[0].certificateSource).toBe('certificateMap');
    expect(commandLoadBalancer.listeners[0].certificate).toBeNull();
    expect(commandLoadBalancer.listeners[0].certificateMap).toBe('cm');
  });

  // The serialize fallback path: when certificateSource is absent, the
  // condition `!listener.certificate && !!listener.certificateMap` at line 89
  // of transformer.service.js infers certificateMap mode. This covers LBs
  // created before the certificateSource field existed.
  it('serializes certificateMap listener without explicit certificateSource via fallback', () => {
    const command = buildBaseCommand({
      name: 'app-main',
      port: 443,
      certificate: null,
      certificateMap: 'cm',
      ipAddress: '10.0.0.1',
      subnet: null,
    });

    const commands = gceHttpLoadBalancerTransformer.serialize(command, null);

    expect(commands.length).toBe(1);
    expect(commands[0].certificate).toBeNull();
    expect(commands[0].certificateMap).toBe('cm');
  });

  it('deserializes cert-only listener and infers certificateSource as certificate', () => {
    const loadBalancer = {
      defaultService: {
        name: 'default-service',
        healthCheck: { name: 'hc-1' },
        sessionAffinity: 'NONE',
      },
      hostRules: [],
      listeners: [
        {
          name: 'app-main',
          certificate: 'legacy-cert',
          certificateMap: null,
        },
      ],
      network: 'default',
      region: 'global',
      urlMapName: 'app',
      account: 'test-account',
    };

    const commandLoadBalancer = gceHttpLoadBalancerTransformer.deserialize(loadBalancer);

    expect(commandLoadBalancer.listeners.length).toBe(1);
    expect(commandLoadBalancer.listeners[0].certificateSource).toBe('certificate');
    expect(commandLoadBalancer.listeners[0].certificate).toBe('legacy-cert');
    expect(commandLoadBalancer.listeners[0].certificateMap).toBeNull();
  });

  it('deserializes listener with neither cert nor map and defaults to certificate source', () => {
    const loadBalancer = {
      defaultService: {
        name: 'default-service',
        healthCheck: { name: 'hc-1' },
        sessionAffinity: 'NONE',
      },
      hostRules: [],
      listeners: [
        {
          name: 'app-main',
          certificate: null,
          certificateMap: null,
        },
      ],
      network: 'default',
      region: 'global',
      urlMapName: 'app',
      account: 'test-account',
    };

    const commandLoadBalancer = gceHttpLoadBalancerTransformer.deserialize(loadBalancer);

    expect(commandLoadBalancer.listeners.length).toBe(1);
    // With neither field set, certificateSource defaults to 'certificate' (HTTP mode).
    expect(commandLoadBalancer.listeners[0].certificateSource).toBe('certificate');
    expect(commandLoadBalancer.listeners[0].certificate).toBeNull();
    expect(commandLoadBalancer.listeners[0].certificateMap).toBeNull();
  });
});

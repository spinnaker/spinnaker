import { GCE_LOAD_BALANCING_POLICY_SELECTOR } from './loadBalancingPolicySelector.component';

describe('gceLoadBalancingPolicySelector', () => {
  let $componentController;

  beforeEach(
    window.module(GCE_LOAD_BALANCING_POLICY_SELECTOR, ($provide) => {
      $provide.value('gceBackendServiceReader', {
        listBackendServices: () => Promise.resolve([]),
      });
    }),
  );

  beforeEach(() => {
    window.inject((_$componentController_) => {
      $componentController = _$componentController_;
    });
  });

  function buildController(command) {
    return $componentController('gceLoadBalancingPolicySelector', null, { command });
  }

  it('treats external managed load balancers as HTTP-family for balancing modes', () => {
    const ctrl = buildController({
      loadBalancers: ['external-url-map'],
      loadBalancingPolicy: { namedPorts: [] },
      backingData: {
        filtered: {
          loadBalancerIndex: {
            'external-url-map': { loadBalancerType: 'EXTERNAL_MANAGED' },
          },
        },
      },
    });

    expect(ctrl.getBalancingModes()).toEqual(['RATE', 'UTILIZATION']);
  });

  it('treats SSL load balancers as passthrough for balancing modes', () => {
    const ctrl = buildController({
      loadBalancers: ['ssl-lb'],
      loadBalancingPolicy: { namedPorts: [] },
      backingData: {
        filtered: {
          loadBalancerIndex: {
            'ssl-lb': { loadBalancerType: 'SSL' },
          },
        },
      },
    });

    expect(ctrl.getBalancingModes()).toEqual(['CONNECTION', 'UTILIZATION']);
  });

  it('limits balancing modes to utilization when HTTP and passthrough load balancers are mixed', () => {
    const ctrl = buildController({
      loadBalancers: ['external-url-map', 'ssl-lb'],
      loadBalancingPolicy: { namedPorts: [] },
      backingData: {
        filtered: {
          loadBalancerIndex: {
            'external-url-map': { loadBalancerType: 'EXTERNAL_MANAGED' },
            'ssl-lb': { loadBalancerType: 'SSL' },
          },
        },
      },
    });

    expect(ctrl.getBalancingModes()).toEqual(['UTILIZATION']);
  });

  it('scopes regional backend service port suggestions by account and region', () => {
    const ctrl = buildController({
      loadBalancers: ['external-url-map'],
      loadBalancingPolicy: { namedPorts: [] },
      backingData: {
        filtered: {
          loadBalancerIndex: {
            'external-url-map': {
              loadBalancerType: 'EXTERNAL_MANAGED',
              account: 'prod',
              region: 'us-central1',
              backendServices: ['shared-backend'],
            },
          },
        },
      },
    });
    ctrl.backendServices = [
      {
        name: 'shared-backend',
        account: 'prod',
        region: 'us-central1',
        kind: 'regionBackendService',
        portName: 'external-http',
      },
      {
        name: 'shared-backend',
        account: 'dev',
        region: 'us-central1',
        kind: 'regionBackendService',
        portName: 'wrong-account',
      },
      {
        name: 'shared-backend',
        account: 'prod',
        region: 'europe-west1',
        kind: 'regionBackendService',
        portName: 'wrong-region',
      },
    ];

    expect(ctrl.getPortNames()).toEqual(['external-http']);
  });

  it('uses regional backend service port suggestions for internal managed load balancers', () => {
    const ctrl = buildController({
      loadBalancers: ['internal-url-map'],
      loadBalancingPolicy: { namedPorts: [] },
      backingData: {
        filtered: {
          loadBalancerIndex: {
            'internal-url-map': {
              loadBalancerType: 'INTERNAL_MANAGED',
              account: 'prod',
              region: 'us-central1',
              backendServices: ['internal-backend'],
            },
          },
        },
      },
    });
    ctrl.backendServices = [
      {
        name: 'internal-backend',
        account: 'prod',
        region: 'us-central1',
        kind: 'regionBackendService',
        portName: 'internal-http',
      },
    ];

    expect(ctrl.getPortNames()).toEqual(['internal-http']);
  });
});

import { mock } from 'angular';

describe('Component: gceLoadBalancerType', () => {
  let $componentController;

  beforeEach(mock.module(require('./loadBalancerType.component').name));

  beforeEach(
    mock.inject((_$componentController_) => {
      $componentController = _$componentController_;
    }),
  );

  function buildController(loadBalancer) {
    return $componentController(
      'gceLoadBalancerType',
      {},
      {
        loadBalancer,
      },
    );
  }

  it('detects HTTPS when certificate is present', () => {
    const ctrl = buildController({
      loadBalancerType: 'HTTP',
      certificate: 'legacy-cert',
      certificateMap: null,
    });

    ctrl.$onInit();

    expect(ctrl.type).toBe('HTTPS');
  });

  it('detects HTTPS when certificateMap is present', () => {
    const ctrl = buildController({
      loadBalancerType: 'HTTP',
      certificate: null,
      certificateMap: 'cm',
    });

    ctrl.$onInit();

    expect(ctrl.type).toBe('HTTPS');
  });

  it('detects HTTP when neither certificate field is present', () => {
    const ctrl = buildController({
      loadBalancerType: 'HTTP',
      certificate: null,
      certificateMap: null,
    });

    ctrl.$onInit();

    expect(ctrl.type).toBe('HTTP');
  });

  it('renders regional external HTTPS when an external managed listener has a certificate', () => {
    const ctrl = buildController({
      loadBalancerType: 'EXTERNAL_MANAGED',
      certificate: null,
      certificateMap: null,
      listeners: [{ certificate: 'regional-cert', certificateMap: null }],
    });

    ctrl.$onInit();

    expect(ctrl.type).toBe('Regional External HTTPS');
  });

  it('renders regional external HTTP when external managed listeners have no certificate', () => {
    const ctrl = buildController({
      loadBalancerType: 'EXTERNAL_MANAGED',
      certificate: null,
      certificateMap: null,
      listeners: [{ certificate: null, certificateMap: null }],
    });

    ctrl.$onInit();

    expect(ctrl.type).toBe('Regional External HTTP');
  });

  it('renders regional external TCP/UDP for regional external network load balancers', () => {
    const ctrl = buildController({
      loadBalancerType: 'REGIONAL_EXTERNAL_NETWORK',
    });

    ctrl.$onInit();

    expect(ctrl.type).toBe('Regional External TCP/UDP');
  });

  // Both fields populated simultaneously — the || means either triggers HTTPS.
  it('detects HTTPS when both certificate and certificateMap are present', () => {
    const ctrl = buildController({
      loadBalancerType: 'HTTP',
      certificate: 'legacy-cert',
      certificateMap: 'cm',
    });

    ctrl.$onInit();

    expect(ctrl.type).toBe('HTTPS');
  });

  // Non-HTTP types pass through the else branch and return verbatim.
  it('returns loadBalancerType verbatim for non-HTTP types', () => {
    const ctrl = buildController({
      loadBalancerType: 'SSL',
      certificate: null,
      certificateMap: null,
    });

    ctrl.$onInit();

    expect(ctrl.type).toBe('SSL');
  });
});

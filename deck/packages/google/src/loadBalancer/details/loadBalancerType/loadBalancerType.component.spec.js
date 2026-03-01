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

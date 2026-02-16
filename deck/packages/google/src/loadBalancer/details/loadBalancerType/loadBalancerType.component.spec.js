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
});

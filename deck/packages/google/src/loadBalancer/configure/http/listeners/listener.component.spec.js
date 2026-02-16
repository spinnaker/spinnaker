import { mock } from 'angular';

describe('Component: gceListener', () => {
  let $componentController;

  beforeEach(mock.module(require('./listener.component').name));

  beforeEach(
    mock.inject((_$componentController_) => {
      $componentController = _$componentController_;
    }),
  );

  function buildBindings(listenerOverrides = {}, loadBalancerOverrides = {}) {
    return {
      command: {
        backingData: {
          certificates: [],
          loadBalancerMap: {},
          subnetMap: { default: [] },
          addresses: [],
        },
        loadBalancer: {
          credentials: 'test-account',
          loadBalancerType: 'HTTP',
          network: 'default',
          region: 'us-central1',
          ...loadBalancerOverrides,
        },
      },
      listener: {
        name: 'app-main',
        port: 443,
        certificate: null,
        certificateMap: null,
        created: false,
        ...listenerOverrides,
      },
      deleteListener: () => {},
      index: 0,
      application: { name: 'app' },
    };
  }

  function buildController(listenerOverrides = {}, loadBalancerOverrides = {}) {
    const ctrl = $componentController('gceListener', {}, buildBindings(listenerOverrides, loadBalancerOverrides));
    ctrl.$onInit();
    return ctrl;
  }

  it('clears certificate when switching to certificateMap source', () => {
    const ctrl = buildController({ certificate: 'legacy-cert', certificateSource: 'certificate' });

    ctrl.listener.certificateSource = 'certificateMap';
    ctrl.onCertificateSourceChanged(ctrl.listener);

    expect(ctrl.listener.certificate).toBeNull();
  });

  it('clears certificateMap when switching to certificate source', () => {
    const ctrl = buildController({
      certificate: null,
      certificateMap: 'cm',
      certificateSource: 'certificateMap',
    });

    ctrl.listener.certificateSource = 'certificate';
    ctrl.onCertificateSourceChanged(ctrl.listener);

    expect(ctrl.listener.certificateMap).toBeNull();
  });

  it('forces certificate source for internal managed load balancers', () => {
    const ctrl = buildController(
      {
        certificateMap: 'cm',
        certificateSource: 'certificateMap',
      },
      { loadBalancerType: 'INTERNAL_MANAGED' },
    );

    expect(ctrl.listener.certificateSource).toBe('certificate');
    expect(ctrl.listener.certificateMap).toBeNull();
  });

  it('clears cert fields when listener port changes away from HTTPS', () => {
    const ctrl = buildController({
      certificate: 'legacy-cert',
      certificateMap: 'cm',
      certificateSource: 'certificateMap',
    });

    ctrl.listener.port = 80;
    ctrl.onPortChanged(ctrl.listener);

    expect(ctrl.listener.certificate).toBeNull();
    expect(ctrl.listener.certificateMap).toBeNull();
    expect(ctrl.listener.certificateSource).toBe('certificate');
  });

  it('normalizes certificateMap URLs to map names', () => {
    const ctrl = buildController({
      certificate: null,
      certificateMap: '//certificatemanager.googleapis.com/projects/p/locations/global/certificateMaps/cm',
      certificateSource: 'certificateMap',
    });

    expect(ctrl.listener.certificateMap).toBe('cm');
  });
});

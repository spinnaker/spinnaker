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
          certificates: [
            { name: 'global-cert', account: 'test-account' },
            { name: 'regional-cert', account: 'test-account', region: 'us-central1' },
            { name: 'other-region-cert', account: 'test-account', region: 'us-east1' },
          ],
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

  it('forces certificate source for external managed load balancers', () => {
    const ctrl = buildController(
      {
        certificateMap: 'cm',
        certificateSource: 'certificateMap',
      },
      { loadBalancerType: 'EXTERNAL_MANAGED' },
    );

    expect(ctrl.listener.certificateSource).toBe('certificate');
    expect(ctrl.listener.certificateMap).toBeNull();
    expect(ctrl.supportsCertificateMap()).toBe(false);
  });

  it('only suggests regional certificates for external managed load balancers', () => {
    const ctrl = buildController({}, { loadBalancerType: 'EXTERNAL_MANAGED', region: 'us-central1' });

    expect(ctrl.getCertificates()).toEqual(['regional-cert']);
  });

  it('only suggests global certificates for global HTTP load balancers', () => {
    const ctrl = buildController({}, { loadBalancerType: 'HTTP' });

    expect(ctrl.getCertificates()).toEqual(['global-cert']);
  });

  it('keeps pasted certificate manager resources as certificate values', () => {
    const certificate = '//certificatemanager.googleapis.com/projects/p/locations/us-central1/certificates/cert-1';
    const ctrl = buildController({ certificate }, { loadBalancerType: 'EXTERNAL_MANAGED' });

    ctrl.onCertificateSelected(ctrl.listener);

    expect(ctrl.listener.certificate).toBe(certificate);
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

  it('stores network tier from selected external addresses', () => {
    const ctrl = buildController({}, { loadBalancerType: 'EXTERNAL_MANAGED' });

    ctrl.onAddressSelect({ address: '34.0.0.1', networkTier: 'STANDARD' });

    expect(ctrl.listener.ipAddress).toBe('34.0.0.1');
    expect(ctrl.listener.networkTier).toBe('STANDARD');
  });

  it('validates certificateMap names with expected regex edge cases', () => {
    const ctrl = buildController();

    const validNames = ['a', 'my-map', 'm1', 'map-123', 'a-b-c-1'];
    const invalidNames = ['', '1map', '-map', 'map-', 'map_name', 'map.name', 'Map'];

    validNames.forEach((name) => {
      expect(ctrl.certificateMapPattern.test(name)).toBe(true);
    });

    invalidNames.forEach((name) => {
      expect(ctrl.certificateMapPattern.test(name)).toBe(false);
    });
  });
});

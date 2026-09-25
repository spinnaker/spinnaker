import { extractDataFromLoadBalancerManifest, interpolate } from './interpolate';

describe('interpolate', () => {
  it('replaces simple template variables from the context', () => {
    expect(
      interpolate('{{displayName}}.{{namespace}}.svc.cluster.local', { displayName: 'backend', namespace: 'dev' }),
    ).toBe('backend.dev.svc.cluster.local');
  });

  it('replaces every occurrence of a variable', () => {
    expect(interpolate('{{name}}-{{name}}', { name: 'backend' })).toBe('backend-backend');
  });

  it('substitutes an empty string for missing variables', () => {
    expect(interpolate('{{displayName}}.{{namespace}}', { displayName: 'backend' })).toBe('backend.');
  });

  it('applies a replace filter to transform the interpolated value', () => {
    expect(interpolate('{{account | replace:"-cluster":""}}', { account: 'gke1-cluster' })).toBe('gke1');
  });

  it('chains multiple replace filters', () => {
    const template =
      '{{displayName}}.{{namespace}}.svc.{{account | replace:"-cluster-v2":"" | replace:"-cluster":""}}.kub.zone.example.com';
    expect(interpolate(template, { displayName: 'backend', namespace: 'dev', account: 'gke1-cluster-v2' })).toBe(
      'backend.dev.svc.gke1.kub.zone.example.com',
    );
  });
});

describe('extractDataFromLoadBalancerManifest', () => {
  it('extracts account, display name, and namespace from a load balancer manifest', () => {
    const manifest = {
      account: 'gke1',
      manifest: {
        metadata: {
          name: 'backend',
          namespace: 'dev',
        },
      },
    };
    expect(extractDataFromLoadBalancerManifest(manifest)).toEqual({
      account: 'gke1',
      displayName: 'backend',
      namespace: 'dev',
    });
  });

  it('returns null when the manifest does not expose the expected fields', () => {
    expect(extractDataFromLoadBalancerManifest({})).toBeNull();
    expect(extractDataFromLoadBalancerManifest(null)).toBeNull();
  });

  it('returns null when the manifest metadata is missing a name or namespace', () => {
    expect(extractDataFromLoadBalancerManifest({ account: 'gke1', manifest: { metadata: {} } })).toBeNull();
    expect(
      extractDataFromLoadBalancerManifest({ account: 'gke1', manifest: { metadata: { name: 'backend' } } }),
    ).toBeNull();
    expect(
      extractDataFromLoadBalancerManifest({ account: 'gke1', manifest: { metadata: { namespace: 'dev' } } }),
    ).toBeNull();
    expect(
      extractDataFromLoadBalancerManifest({
        account: 'gke1',
        manifest: { metadata: { name: 'backend', namespace: '' } },
      }),
    ).toBeNull();
  });

  it('returns null when the manifest is not wrapped in a manifest property', () => {
    expect(
      extractDataFromLoadBalancerManifest({ account: 'gke1', metadata: { name: 'backend', namespace: 'dev' } }),
    ).toBeNull();
  });
});

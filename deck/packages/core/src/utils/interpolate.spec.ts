import { interpolate } from './interpolate';

describe('interpolate', () => {
  it('resolves dotted properties', () => {
    expect(interpolate('service={{ service.name }}')({ service: { name: 'octopus' } })).toBe('service=octopus');
  });

  it('renders missing properties as empty strings', () => {
    expect(interpolate('service={{ service.missing }}')({ service: {} })).toBe('service=');
  });

  it('renders null properties as empty strings', () => {
    expect(interpolate('service={{ service.name }}')({ service: { name: null } })).toBe('service=');
  });

  it('applies a replace filter to an interpolated value', () => {
    expect(interpolate('{{ account | replace:"-cluster":"" }}', { account: 'gke1-cluster' })).toBe('gke1');
  });

  it('chains multiple replace filters', () => {
    const template = '{{ account | replace:"-cluster-v2":"" | replace:"-cluster":"" }}';
    expect(interpolate(template, { account: 'gke1-cluster-v2' })).toBe('gke1');
  });

  it('resolves an expression and filters directly when called with a context', () => {
    expect(interpolate('{{ service | replace:"s":"x" }}', { service: 'backend' })).toBe('backend');
  });

  it('renders null values as empty strings when filters are present', () => {
    expect(interpolate('{{ service | replace:"a":"b" }}', { service: null })).toBe('');
  });
});

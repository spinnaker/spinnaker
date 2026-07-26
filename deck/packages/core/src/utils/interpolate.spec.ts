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
});

import { formatGceInstanceType } from './customInstanceFormatter';

describe('formatGceInstanceType', () => {
  it('returns predefined machine types unchanged', () => {
    expect(formatGceInstanceType('n2-standard-8')).toBe('n2-standard-8');
  });

  it('returns undefined for missing machine types', () => {
    expect(formatGceInstanceType(undefined)).toBeUndefined();
    expect(formatGceInstanceType('')).toBeUndefined();
  });

  it('formats N1 and family-prefixed custom machine types', () => {
    expect(formatGceInstanceType('custom-2-4096')).toBe('N1 2 vCPU / 4 GB RAM');
    expect(formatGceInstanceType('e2-custom-4-6144')).toBe('E2 4 vCPU / 6 GB RAM');
  });

  it('formats extended-memory custom machine types', () => {
    expect(formatGceInstanceType('n2-custom-2-20480-ext')).toBe('N2 2 vCPU / 20 GB RAM (extended)');
  });
});

import { decimalToPercent } from './percent.filter';
import { replaceValue } from './replace.filter';

describe('presentation filter helpers', () => {
  it('formats decimal values as rounded percentages', () => {
    expect(decimalToPercent(0.123)).toBe('12%');
    expect(decimalToPercent(0.5)).toBe('50%');
  });

  it('replaces all regex matches when pattern and replacement are provided', () => {
    expect(replaceValue('alpha-beta-beta', 'beta', 'gamma')).toBe('alpha-gamma-gamma');
  });

  it('returns the original string when replace arguments are missing', () => {
    expect(replaceValue('alpha-beta', undefined, 'gamma')).toBe('alpha-beta');
    expect(replaceValue('alpha-beta', 'beta', undefined)).toBe('alpha-beta');
  });
});

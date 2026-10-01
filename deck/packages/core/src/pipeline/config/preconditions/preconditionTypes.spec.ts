import { getPreconditionType, getPreconditionTypeLabel, listPreconditionTypes } from './preconditionTypes';

describe('preconditionTypes', () => {
  it('lists the built-in precondition types in the legacy selector order', () => {
    expect(listPreconditionTypes()).toEqual([
      expect.objectContaining({ key: 'clusterSize', label: 'Cluster Size' }),
      expect.objectContaining({ key: 'expression', label: 'Expression' }),
      expect.objectContaining({ key: 'stageStatus', label: 'Stage Status' }),
    ]);
  });

  it('returns registered precondition types by key', () => {
    expect(getPreconditionType('expression')).toEqual(expect.objectContaining({ label: 'Expression' }));
    expect(getPreconditionType('missing')).toBeUndefined();
  });

  it('uses registered labels and falls back to the legacy capitalized key label', () => {
    expect(getPreconditionTypeLabel('stageStatus')).toBe('Stage Status');
    expect(getPreconditionTypeLabel('custom')).toBe('Custom');
  });
});

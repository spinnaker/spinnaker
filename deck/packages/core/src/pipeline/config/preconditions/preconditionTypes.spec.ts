import { getPreconditionType, getPreconditionTypeLabel, listPreconditionTypes } from './preconditionTypes';

describe('preconditionTypes', () => {
  it('lists the built-in precondition types in the legacy selector order', () => {
    expect(listPreconditionTypes()).toEqual([
      jasmine.objectContaining({ key: 'clusterSize', label: 'Cluster Size' }),
      jasmine.objectContaining({ key: 'expression', label: 'Expression' }),
      jasmine.objectContaining({ key: 'stageStatus', label: 'Stage Status' }),
    ]);
  });

  it('returns registered precondition types by key', () => {
    expect(getPreconditionType('expression')).toEqual(jasmine.objectContaining({ label: 'Expression' }));
    expect(getPreconditionType('missing')).toBeUndefined();
  });

  it('uses registered labels and falls back to the legacy capitalized key label', () => {
    expect(getPreconditionTypeLabel('stageStatus')).toBe('Stage Status');
    expect(getPreconditionTypeLabel('custom')).toBe('Custom');
  });
});

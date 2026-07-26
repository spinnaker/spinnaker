import { ManualExecutionBake } from './ManualExecutionBake';
import { bakeStage } from './bakeStage';

describe('Bake stage registration', () => {
  it('exports the Bake stage config', () => {
    expect(bakeStage).toEqual(
      jasmine.objectContaining({
        useBaseProvider: true,
        label: 'Bake',
        description: 'Bakes an image',
        key: 'bake',
        restartable: true,
        manualExecutionComponent: ManualExecutionBake,
      }),
    );
  });
});

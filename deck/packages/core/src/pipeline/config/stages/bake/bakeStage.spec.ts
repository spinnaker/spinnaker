import { ManualExecutionBake } from './ManualExecutionBake';
import { bakeStage } from './bakeStage';

describe('Bake stage registration', () => {
  it('exports the Bake stage config without Angular-only fields', () => {
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
    expect((bakeStage as any).templateUrl).toBeUndefined();
    expect((bakeStage as any).controller).toBeUndefined();
    expect((bakeStage as any).executionDetailsUrl).toBeUndefined();
  });
});

import { travisStage } from './travisStage';
import { TravisExecutionDetails } from './TravisExecutionDetails';
import { TravisExecutionLabel } from './TravisExecutionLabel';
import { TravisStageConfig } from './TravisStageConfig';

describe('Travis stage registration', () => {
  it('registers Travis as a React-configured restartable CI stage', () => {
    expect(travisStage).toEqual(
      jasmine.objectContaining({
        key: 'travis',
        label: 'Travis',
        description: 'Runs a Travis job',
        restartable: true,
        producesArtifacts: true,
        executionLabelComponent: TravisExecutionLabel,
        providesVersionForBake: true,
        supportsCustomTimeout: true,
        validators: [{ type: 'requiredField', fieldName: 'job' }],
        strategy: true,
      }),
    );
    expect(travisStage.component).toBe(TravisStageConfig);
    expect(travisStage.executionDetailsSections[0]).toBe(TravisExecutionDetails);
    expect(travisStage.executionDetailsSections.length).toBeGreaterThan(1);
    expect((travisStage as any).templateUrl).toBeUndefined();
    expect((travisStage as any).executionDetailsSections).toBeDefined();
  });

  it('preserves Travis extra execution label lines', () => {
    expect(travisStage.extraLabelLines({ masterStage: { context: {} } } as any)).toBe(0);
    expect(
      travisStage.extraLabelLines({
        masterStage: { context: { buildInfo: { number: 12, testResults: [{}, {}] } } },
      } as any),
    ).toBe(3);
  });
});

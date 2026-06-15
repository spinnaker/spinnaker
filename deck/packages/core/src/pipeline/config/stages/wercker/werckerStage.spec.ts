import { werckerStage } from './werckerStage';
import { WerckerExecutionDetails } from './WerckerExecutionDetails';
import { WerckerExecutionLabel } from './WerckerExecutionLabel';
import { WerckerStageConfig } from './WerckerStageConfig';

describe('Wercker stage registration', () => {
  it('registers Wercker as a React-configured restartable CI stage', () => {
    expect(werckerStage).toEqual(
      jasmine.objectContaining({
        key: 'wercker',
        label: 'Wercker',
        description: 'Runs a Wercker build pipeline',
        restartable: true,
        executionLabelComponent: WerckerExecutionLabel,
        supportsCustomTimeout: true,
        validators: [{ type: 'requiredField', fieldName: 'job' }],
        strategy: true,
      }),
    );
    expect(werckerStage.component).toBe(WerckerStageConfig);
    expect(werckerStage.executionDetailsSections[0]).toBe(WerckerExecutionDetails);
    expect(werckerStage.executionDetailsSections.length).toBeGreaterThan(1);
    expect((werckerStage as any).templateUrl).toBeUndefined();
    expect((werckerStage as any).executionDetailsUrl).toBeUndefined();
  });

  it('preserves Wercker extra execution label lines', () => {
    expect(werckerStage.extraLabelLines({ masterStage: { context: {} } } as any)).toBe(0);
    expect(
      werckerStage.extraLabelLines({
        masterStage: { context: { buildInfo: { number: 12, testResults: [{}, {}] } } },
      } as any),
    ).toBe(3);
  });
});

import { ExecutionArtifactTab } from '../../../../artifact/react/ExecutionArtifactTab';
import { JenkinsExecutionDetails } from './JenkinsExecutionDetails';
import { jenkinsStage } from './jenkinsStage';
import { JenkinsExecutionLabel } from './JenkinsExecutionLabel';
import { JenkinsStageConfig } from './JenkinsStageConfig';

describe('Jenkins stage registration', () => {
  it('registers Jenkins as a React-configured restartable CI stage', () => {
    expect(jenkinsStage).toEqual(
      jasmine.objectContaining({
        key: 'jenkins',
        label: 'Jenkins',
        description: 'Runs a Jenkins job',
        restartable: true,
        producesArtifacts: true,
        executionLabelComponent: JenkinsExecutionLabel,
        providesVersionForBake: true,
        supportsCustomTimeout: true,
        validators: [{ type: 'requiredField', fieldName: 'job' }],
        strategy: true,
      }),
    );
    expect(jenkinsStage.component).toBe(JenkinsStageConfig);
    expect(jenkinsStage.executionDetailsSections[0]).toBe(JenkinsExecutionDetails);
    expect(jenkinsStage.executionDetailsSections).toContain(ExecutionArtifactTab);
    expect(jenkinsStage.templateUrl).toBeUndefined();
    expect(jenkinsStage.executionDetailsUrl).toBeUndefined();
  });

  it('preserves Jenkins extra execution label lines', () => {
    expect(jenkinsStage.extraLabelLines({ masterStage: { context: {} } })).toBe(0);
    expect(
      jenkinsStage.extraLabelLines({
        masterStage: { context: { buildInfo: { number: 12, testResults: [{}, {}] } } },
      }),
    ).toBe(3);
  });
});

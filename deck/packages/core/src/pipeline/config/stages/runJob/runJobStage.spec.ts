import { runJobStage } from './runJobStage';
import { NoConfigurationStageConfig } from '../common';

describe('Run Job stage registration', () => {
  it('exports the Run Job stage config without Angular-only fields', () => {
    expect(runJobStage).toEqual({
      useBaseProvider: true,
      key: 'runJob',
      label: 'Run Job',
      description: 'Runs a container',
      component: NoConfigurationStageConfig,
      restartable: true,
    });
    expect((runJobStage as any).templateUrl).toBeUndefined();
    expect((runJobStage as any).controller).toBeUndefined();
  });
});

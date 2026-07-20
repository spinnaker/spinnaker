import { runJobStage } from './runJobStage';

describe('Run Job stage registration', () => {
  it('exports the Run Job stage config without Angular-only fields', () => {
    expect(runJobStage).toEqual({
      useBaseProvider: true,
      key: 'runJob',
      label: 'Run Job',
      description: 'Runs a container',
      restartable: true,
    });
    expect((runJobStage as any).templateUrl).toBeUndefined();
    expect((runJobStage as any).controller).toBeUndefined();
    expect((runJobStage as any).executionDetailsUrl).toBeUndefined();
  });
});

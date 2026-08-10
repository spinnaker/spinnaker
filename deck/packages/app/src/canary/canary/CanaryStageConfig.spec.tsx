import { initializeCanaryStage } from './CanaryStageConfig';

describe('CanaryStageConfig', () => {
  it('initializes a missing canary config for an existing stage', () => {
    const stage = { baseline: {}, canary: {}, isNew: false, scaleUp: { enabled: false } } as any;

    expect(() => initializeCanaryStage(stage, { name: 'Pipeline' }, { authenticated: false })).not.toThrow();
    expect(stage.canary.canaryConfig.canaryAnalysisConfig.notificationHours).toEqual([1, 2, 3]);
  });

  it('initializes a missing canary analysis config for an existing stage', () => {
    const stage = {
      baseline: {},
      canary: {
        canaryConfig: {
          name: 'Existing canary config',
          lifetimeHours: 3,
          canaryHealthCheckHandler: { minimumCanaryResultScore: 75 },
          canarySuccessCriteria: { canaryResultScore: 95 },
        },
      },
      isNew: false,
      scaleUp: { enabled: false },
    } as any;

    expect(() => initializeCanaryStage(stage, { name: 'Pipeline' }, { authenticated: false })).not.toThrow();
    expect(stage.canary.canaryConfig.name).toBe('Existing canary config');
    expect(stage.canary.canaryConfig.canaryAnalysisConfig.notificationHours).toEqual([1, 2, 3]);
  });

  it('initializes missing notification hours for an existing canary analysis config', () => {
    const stage = {
      baseline: {},
      canary: {
        canaryConfig: {
          name: 'Existing canary config',
          lifetimeHours: 3,
          canaryHealthCheckHandler: { minimumCanaryResultScore: 75 },
          canarySuccessCriteria: { canaryResultScore: 95 },
          canaryAnalysisConfig: { name: 'Existing analysis config' },
        },
      },
      isNew: false,
      scaleUp: { enabled: false },
    } as any;

    expect(() => initializeCanaryStage(stage, { name: 'Pipeline' }, { authenticated: false })).not.toThrow();
    expect(stage.canary.canaryConfig.canaryAnalysisConfig.name).toBe('Existing analysis config');
    expect(stage.canary.canaryConfig.canaryAnalysisConfig.notificationHours).toEqual([1, 2, 3]);
  });
});

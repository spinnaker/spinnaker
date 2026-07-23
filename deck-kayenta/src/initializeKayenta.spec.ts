jest.mock('kayenta/canary.dataSource.stub', () => ({ registerKayentaDataSourceStubs: jest.fn() }));
jest.mock('kayenta/canary.help', () => ({}));
jest.mock('kayenta/navigation/canary.states.stub', () => ({ registerKayentaStateStubs: jest.fn() }));
jest.mock('kayenta/canary.settings', () => ({ CanarySettings: { featureDisabled: false, stagesEnabled: true } }));
jest.mock('./kayenta/stages/kayentaStage/kayentaStage', () => ({ kayentaCanaryStage: {} }));
jest.mock('./kayenta/stages/kayentaStage/kayentaStage.transformer', () => ({
  KayentaStageTransformer: jest.fn().mockImplementation(() => ({})),
}));
jest.mock('@spinnaker/core', () => ({
  Registry: {
    pipeline: {
      registerStage: jest.fn(),
      registerTransformer: jest.fn(),
    },
  },
  registerApplicationInitializer: jest.fn(),
}));

describe('initializeKayenta', () => {
  beforeEach(() => {
    jest.resetModules();
    jest.clearAllMocks();
  });

  it('registers data source stubs and route stubs when Kayenta is enabled', async () => {
    const { initializeKayenta } = await import('./initializeKayenta');
    const { registerKayentaDataSourceStubs } = await import('kayenta/canary.dataSource.stub');
    const { registerKayentaStateStubs } = await import('kayenta/navigation/canary.states.stub');

    const applicationState = {} as any;
    const uiRouter = {} as any;

    initializeKayenta(applicationState, uiRouter);

    expect(registerKayentaDataSourceStubs).toHaveBeenCalledWith(uiRouter);
    expect(registerKayentaStateStubs).toHaveBeenCalledWith(applicationState, uiRouter);
  });

  it('skips all registration when Kayenta is disabled', async () => {
    const { CanarySettings } = await import('kayenta/canary.settings');
    CanarySettings.featureDisabled = true;

    const { initializeKayenta } = await import('./initializeKayenta');
    const { registerKayentaDataSourceStubs } = await import('kayenta/canary.dataSource.stub');
    const { registerKayentaStateStubs } = await import('kayenta/navigation/canary.states.stub');
    const { Registry } = await import('@spinnaker/core');

    initializeKayenta({} as any, {} as any);

    expect(registerKayentaDataSourceStubs).not.toHaveBeenCalled();
    expect(registerKayentaStateStubs).not.toHaveBeenCalled();
    expect(Registry.pipeline.registerStage).not.toHaveBeenCalled();
    expect(Registry.pipeline.registerTransformer).not.toHaveBeenCalled();
  });

  it('registers the Kayenta stage and transformer when stages are enabled', async () => {
    const { initializeKayenta } = await import('./initializeKayenta');
    const { Registry } = await import('@spinnaker/core');
    const { kayentaCanaryStage } = await import('./kayenta/stages/kayentaStage/kayentaStage');

    initializeKayenta({} as any, {} as any);

    expect(Registry.pipeline.registerStage).toHaveBeenCalledTimes(1);
    expect(Registry.pipeline.registerStage).toHaveBeenCalledWith(kayentaCanaryStage);
    expect(Registry.pipeline.registerTransformer).toHaveBeenCalledTimes(1);
  });

  it('skips stage registration when stages are disabled', async () => {
    const { CanarySettings } = await import('kayenta/canary.settings');
    CanarySettings.stagesEnabled = false;

    const { initializeKayenta } = await import('./initializeKayenta');
    const { Registry } = await import('@spinnaker/core');

    initializeKayenta({} as any, {} as any);

    expect(Registry.pipeline.registerStage).not.toHaveBeenCalled();
    expect(Registry.pipeline.registerTransformer).not.toHaveBeenCalled();
  });

  it('registers Kayenta initialization when the stub is imported', async () => {
    await import('./stub');

    const { registerApplicationInitializer } = await import('@spinnaker/core');
    const { initializeKayenta } = await import('./initializeKayenta');

    expect(registerApplicationInitializer).toHaveBeenCalledWith(initializeKayenta);
  });
});

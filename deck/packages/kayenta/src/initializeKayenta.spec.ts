import * as Core from '@spinnaker/core';

import { createKayentaInitializer, initializeKayenta } from './initializeKayenta';
import { registerKayentaDataSourceStubs } from './kayenta/canary.dataSource.stub';
import { CanarySettings } from './kayenta/canary.settings';
import { KayentaStageTransformer } from './kayenta/stages/kayentaStage/kayentaStage.transformer';

describe('initializeKayenta', () => {
  const createDependencies = (settings = { featureDisabled: false, stagesEnabled: true }) => ({
    settings,
    registerDataSourceStubs: vi.fn(),
    registerStateStubs: vi.fn(),
    registerStage: vi.fn(),
    registerTransformer: vi.fn(),
    createStageTransformer: vi.fn().mockReturnValue({ transformer: true } as any),
    stage: { key: 'kayentaCanary' } as any,
  });

  it('registers data source and route stubs once when Kayenta is enabled', () => {
    const dependencies = createDependencies();
    const initializer = createKayentaInitializer(dependencies);
    const applicationState = {} as any;
    const uiRouter = {} as any;

    initializer(applicationState, uiRouter);
    initializer(applicationState, uiRouter);

    expect(dependencies.registerDataSourceStubs).toHaveBeenCalledExactlyOnceWith(uiRouter);
    expect(dependencies.registerStateStubs).toHaveBeenCalledExactlyOnceWith(applicationState, uiRouter);
  });

  it('skips all registration when Kayenta is disabled', () => {
    const dependencies = createDependencies({ featureDisabled: true, stagesEnabled: true });

    createKayentaInitializer(dependencies)({} as any, {} as any);

    expect(dependencies.registerDataSourceStubs).not.toHaveBeenCalled();
    expect(dependencies.registerStateStubs).not.toHaveBeenCalled();
    expect(dependencies.registerStage).not.toHaveBeenCalled();
    expect(dependencies.registerTransformer).not.toHaveBeenCalled();
  });

  it('registers the Kayenta stage and transformer when stages are enabled', () => {
    const dependencies = createDependencies();

    createKayentaInitializer(dependencies)({} as any, {} as any);

    expect(dependencies.registerStage).toHaveBeenCalledExactlyOnceWith(dependencies.stage);
    expect(dependencies.createStageTransformer).toHaveBeenCalledTimes(1);
    expect(dependencies.registerTransformer).toHaveBeenCalledExactlyOnceWith(
      expect.objectContaining({ transformer: true }),
    );
  });

  it('skips stage registration when stages are disabled', () => {
    const dependencies = createDependencies({ featureDisabled: false, stagesEnabled: false });

    createKayentaInitializer(dependencies)({} as any, {} as any);

    expect(dependencies.registerStage).not.toHaveBeenCalled();
    expect(dependencies.registerTransformer).not.toHaveBeenCalled();
  });

  it('wires the default initializer to the current Core registries with stable stage keys', () => {
    const originalPipeline = Core.Registry.pipeline;
    const originalUrlBuilder = Core.Registry.urlBuilder;
    const originalFeatureDisabled = CanarySettings.featureDisabled;
    const originalStagesEnabled = CanarySettings.stagesEnabled;
    const registerDataSource = vi
      .spyOn(Core.ApplicationDataSourceRegistry, 'registerDataSource')
      .mockReturnValue(undefined);
    const registerState = vi.fn();

    CanarySettings.featureDisabled = false;
    CanarySettings.stagesEnabled = true;
    Core.Registry.reinitialize();

    try {
      const registerStage = vi.spyOn(Core.Registry.pipeline, 'registerStage').mockReturnValue(undefined);
      const registerTransformer = vi.spyOn(Core.Registry.pipeline, 'registerTransformer').mockReturnValue(undefined);

      initializeKayenta(
        {} as any,
        {
          stateRegistry: { register: registerState },
          stateService: { params: {} },
        } as any,
      );

      expect(registerDataSource.mock.calls.map(([dataSource]) => dataSource.key)).toEqual([
        'canaryConfigs',
        'canaryJudges',
        'canaryExecutions',
      ]);
      expect(registerState).toHaveBeenCalledTimes(14);
      expect(registerStage.mock.calls.map(([stage]) => stage.key)).toEqual(['kayentaCanary']);
      expect(registerTransformer).toHaveBeenCalledExactlyOnceWith(expect.any(KayentaStageTransformer));
    } finally {
      Core.Registry.pipeline = originalPipeline;
      Core.Registry.urlBuilder = originalUrlBuilder;
      CanarySettings.featureDisabled = originalFeatureDisabled;
      CanarySettings.stagesEnabled = originalStagesEnabled;
    }

    expect(Core.Registry.pipeline).toBe(originalPipeline);
    expect(Core.Registry.urlBuilder).toBe(originalUrlBuilder);
  });

  it('registers Kayenta initialization when the stub is imported', async () => {
    const registerInitializer = vi.fn();
    // Intercept the core export a fresh ./stub evaluation will invoke at import time.
    vi.doMock('@spinnaker/core', async () => {
      const actual = await vi.importActual<typeof Core>('@spinnaker/core');
      return { ...actual, registerApplicationInitializer: registerInitializer };
    });
    vi.resetModules();

    try {
      const stubExports = await import('./stub');
      const { initializeKayenta: freshInitializeKayenta } = await import('./initializeKayenta');

      expect(registerInitializer).toHaveBeenCalledExactlyOnceWith(freshInitializeKayenta);
      expect((stubExports as any).registerKayentaInitializer).toBeUndefined();
    } finally {
      vi.doUnmock('@spinnaker/core');
      vi.resetModules();
    }
  });

  it('retains the stable Kayenta data source keys', () => {
    const registerDataSource = vi
      .spyOn(Core.ApplicationDataSourceRegistry, 'registerDataSource')
      .mockReturnValue(undefined);

    registerKayentaDataSourceStubs({ stateService: { params: {} } });

    expect(registerDataSource.mock.calls.map(([dataSource]) => dataSource.key)).toEqual([
      'canaryConfigs',
      'canaryJudges',
      'canaryExecutions',
    ]);
  });

  it('retains the expected root exports without exposing or registering internal bootstrap seams', async () => {
    const registerInitializer = vi.fn();
    vi.doMock('@spinnaker/core', async () => {
      const actual = await vi.importActual<typeof Core>('@spinnaker/core');
      return { ...actual, registerApplicationInitializer: registerInitializer };
    });
    vi.resetModules();

    try {
      const publicExports = await import('./index');
      const { initializeKayenta: freshInitializeKayenta } = await import('./initializeKayenta');

      expect(publicExports.initializeKayenta).toBe(freshInitializeKayenta);
      expect(publicExports.LOAD_CONFIG_REQUEST).toBe('load_config_request');
      expect(publicExports.KayentaAccountType).toBeDefined();
      expect((publicExports as any).createKayentaInitializer).toBeUndefined();
      expect((publicExports as any).registerKayentaInitializer).toBeUndefined();
      expect(registerInitializer).toHaveBeenCalledExactlyOnceWith(freshInitializeKayenta);
    } finally {
      vi.doUnmock('@spinnaker/core');
      vi.resetModules();
    }
  });
});

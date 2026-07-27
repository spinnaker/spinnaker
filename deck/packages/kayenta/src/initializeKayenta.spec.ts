import * as Core from '@spinnaker/core';

import { createKayentaInitializer, initializeKayenta } from './initializeKayenta';
import { registerKayentaDataSourceStubs } from './kayenta/canary.dataSource.stub';
import { CanarySettings } from './kayenta/canary.settings';
import { KayentaStageTransformer } from './kayenta/stages/kayentaStage/kayentaStage.transformer';

describe('initializeKayenta', () => {
  const createDependencies = (settings = { featureDisabled: false, stagesEnabled: true }) => ({
    settings,
    registerDataSourceStubs: jasmine.createSpy('registerDataSourceStubs'),
    registerStateStubs: jasmine.createSpy('registerStateStubs'),
    registerStage: jasmine.createSpy('registerStage'),
    registerTransformer: jasmine.createSpy('registerTransformer'),
    createStageTransformer: jasmine.createSpy('createStageTransformer').and.returnValue({ transformer: true } as any),
    stage: { key: 'kayentaCanary' } as any,
  });

  it('registers data source and route stubs once when Kayenta is enabled', () => {
    const dependencies = createDependencies();
    const initializer = createKayentaInitializer(dependencies);
    const applicationState = {} as any;
    const uiRouter = {} as any;

    initializer(applicationState, uiRouter);
    initializer(applicationState, uiRouter);

    expect(dependencies.registerDataSourceStubs).toHaveBeenCalledOnceWith(uiRouter);
    expect(dependencies.registerStateStubs).toHaveBeenCalledOnceWith(applicationState, uiRouter);
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

    expect(dependencies.registerStage).toHaveBeenCalledOnceWith(dependencies.stage);
    expect(dependencies.createStageTransformer).toHaveBeenCalledTimes(1);
    expect(dependencies.registerTransformer).toHaveBeenCalledOnceWith(jasmine.objectContaining({ transformer: true }));
  });

  it('skips stage registration when stages are disabled', () => {
    const dependencies = createDependencies({ featureDisabled: false, stagesEnabled: false });

    createKayentaInitializer(dependencies)({} as any, {} as any);

    expect(dependencies.registerStage).not.toHaveBeenCalled();
    expect(dependencies.registerTransformer).not.toHaveBeenCalled();
  });

  it('wires the default initializer to the current Core registries with stable stage keys', () => {
    const initializerModule = require.resolve('./initializeKayenta');
    const originalInitializerModule = require.cache[initializerModule];
    const originalPipeline = Core.Registry.pipeline;
    const originalUrlBuilder = Core.Registry.urlBuilder;
    const originalFeatureDisabled = CanarySettings.featureDisabled;
    const originalStagesEnabled = CanarySettings.stagesEnabled;
    const registerDataSource = spyOn(Core.ApplicationDataSourceRegistry, 'registerDataSource');
    const registerState = jasmine.createSpy('registerState');

    CanarySettings.featureDisabled = false;
    CanarySettings.stagesEnabled = true;
    Core.Registry.reinitialize();
    delete require.cache[initializerModule];

    try {
      const freshInitializeKayenta = require('./initializeKayenta').initializeKayenta;
      Core.Registry.reinitialize();
      const registerStage = spyOn(Core.Registry.pipeline, 'registerStage');
      const registerTransformer = spyOn(Core.Registry.pipeline, 'registerTransformer');

      freshInitializeKayenta(
        {} as any,
        {
          stateRegistry: { register: registerState },
          stateService: { params: {} },
        } as any,
      );

      expect(registerDataSource.calls.allArgs().map(([dataSource]) => dataSource.key)).toEqual([
        'canaryConfigs',
        'canaryJudges',
        'canaryExecutions',
      ]);
      expect(registerState).toHaveBeenCalledTimes(14);
      expect(registerStage.calls.allArgs().map(([stage]) => stage.key)).toEqual(['kayentaCanary']);
      expect(registerTransformer).toHaveBeenCalledOnceWith(jasmine.any(KayentaStageTransformer));
    } finally {
      if (originalInitializerModule) {
        require.cache[initializerModule] = originalInitializerModule;
      } else {
        delete require.cache[initializerModule];
      }
      Core.Registry.pipeline = originalPipeline;
      Core.Registry.urlBuilder = originalUrlBuilder;
      CanarySettings.featureDisabled = originalFeatureDisabled;
      CanarySettings.stagesEnabled = originalStagesEnabled;
    }

    expect(Core.Registry.pipeline).toBe(originalPipeline);
    expect(Core.Registry.urlBuilder).toBe(originalUrlBuilder);
  });

  it('registers Kayenta initialization when the stub is imported', () => {
    const registerInitializer = jasmine.createSpy('registerApplicationInitializer');
    const coreModule = require.cache[require.resolve('@spinnaker/core')];
    const originalCoreExports = coreModule.exports;
    const stubModule = require.resolve('./stub');
    const originalStubModule = require.cache[stubModule];
    const originalPipeline = Core.Registry.pipeline;
    const originalUrlBuilder = Core.Registry.urlBuilder;
    // Webpack barrel exports are read-only, so replace the cached exports while re-evaluating the stub.
    coreModule.exports = new Proxy(originalCoreExports, {
      get: (target, property) =>
        property === 'registerApplicationInitializer' ? registerInitializer : Reflect.get(target, property),
    });
    delete require.cache[stubModule];

    try {
      const stubExports = require('./stub');

      expect(registerInitializer).toHaveBeenCalledOnceWith(initializeKayenta);
      expect(stubExports.registerKayentaInitializer).toBeUndefined();
    } finally {
      coreModule.exports = originalCoreExports;
      if (originalStubModule) {
        require.cache[stubModule] = originalStubModule;
      } else {
        delete require.cache[stubModule];
      }
      Core.Registry.pipeline = originalPipeline;
      Core.Registry.urlBuilder = originalUrlBuilder;
    }

    expect(coreModule.exports).toBe(originalCoreExports);
    expect(require.cache[stubModule]).toBe(originalStubModule);
    expect(Core.Registry.pipeline).toBe(originalPipeline);
    expect(Core.Registry.urlBuilder).toBe(originalUrlBuilder);
  });

  it('retains the stable Kayenta data source keys', () => {
    const registerDataSource = spyOn(Core.ApplicationDataSourceRegistry, 'registerDataSource');

    registerKayentaDataSourceStubs({ stateService: { params: {} } });

    expect(registerDataSource.calls.allArgs().map(([dataSource]) => dataSource.key)).toEqual([
      'canaryConfigs',
      'canaryJudges',
      'canaryExecutions',
    ]);
  });

  it('retains the expected root exports without exposing or registering internal bootstrap seams', () => {
    const registerInitializer = jasmine.createSpy('registerApplicationInitializer');
    const coreModule = require.cache[require.resolve('@spinnaker/core')];
    const originalCoreExports = coreModule.exports;
    const indexModule = require.resolve('./index');
    const originalIndexModule = require.cache[indexModule];
    const stubModule = require.resolve('./stub');
    const originalStubModule = require.cache[stubModule];
    const originalPipeline = Core.Registry.pipeline;
    const originalUrlBuilder = Core.Registry.urlBuilder;
    coreModule.exports = new Proxy(originalCoreExports, {
      get: (target, property) =>
        property === 'registerApplicationInitializer' ? registerInitializer : Reflect.get(target, property),
    });
    delete require.cache[indexModule];
    delete require.cache[stubModule];

    try {
      const publicExports = require('./index');

      expect(publicExports.initializeKayenta).toBe(initializeKayenta);
      expect(publicExports.LOAD_CONFIG_REQUEST).toBe('load_config_request');
      expect(publicExports.KayentaAccountType).toBeDefined();
      expect(publicExports.createKayentaInitializer).toBeUndefined();
      expect(publicExports.registerKayentaInitializer).toBeUndefined();
      expect(registerInitializer).toHaveBeenCalledOnceWith(initializeKayenta);
    } finally {
      coreModule.exports = originalCoreExports;
      if (originalIndexModule) {
        require.cache[indexModule] = originalIndexModule;
      } else {
        delete require.cache[indexModule];
      }
      if (originalStubModule) {
        require.cache[stubModule] = originalStubModule;
      } else {
        delete require.cache[stubModule];
      }
      Core.Registry.pipeline = originalPipeline;
      Core.Registry.urlBuilder = originalUrlBuilder;
    }

    expect(coreModule.exports).toBe(originalCoreExports);
    expect(require.cache[indexModule]).toBe(originalIndexModule);
    expect(require.cache[stubModule]).toBe(originalStubModule);
    expect(Core.Registry.pipeline).toBe(originalPipeline);
    expect(Core.Registry.urlBuilder).toBe(originalUrlBuilder);
  });
});

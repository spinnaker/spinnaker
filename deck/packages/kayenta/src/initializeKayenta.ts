import type { UIRouter } from '@uirouter/core';

import type { ApplicationStateProvider } from '@spinnaker/core';
import { Registry } from '@spinnaker/core';

import { registerKayentaDataSourceStubs } from './kayenta/canary.dataSource.stub';
import './kayenta/canary.help';
import { CanarySettings } from './kayenta/canary.settings';
import { registerKayentaStateStubs } from './kayenta/navigation/canary.states.stub';
import { kayentaCanaryStage } from './kayenta/stages/kayentaStage/kayentaStage';
import { KayentaStageTransformer } from './kayenta/stages/kayentaStage/kayentaStage.transformer';

interface IKayentaInitializerDependencies {
  settings: Pick<typeof CanarySettings, 'featureDisabled' | 'stagesEnabled'>;
  registerDataSourceStubs: typeof registerKayentaDataSourceStubs;
  registerStateStubs: typeof registerKayentaStateStubs;
  registerStage: typeof Registry.pipeline.registerStage;
  registerTransformer: typeof Registry.pipeline.registerTransformer;
  createStageTransformer: () => KayentaStageTransformer;
  stage: Parameters<typeof Registry.pipeline.registerStage>[0];
}

const initializerDependencies: IKayentaInitializerDependencies = {
  settings: CanarySettings,
  registerDataSourceStubs: registerKayentaDataSourceStubs,
  registerStateStubs: registerKayentaStateStubs,
  registerStage: (stage) => Registry.pipeline.registerStage(stage),
  registerTransformer: (transformer) => Registry.pipeline.registerTransformer(transformer),
  createStageTransformer: () => new KayentaStageTransformer(),
  stage: kayentaCanaryStage,
};

export function createKayentaInitializer(dependencies: IKayentaInitializerDependencies) {
  let initialized = false;

  return (applicationState: ApplicationStateProvider, uiRouter: UIRouter): void => {
    if (initialized || dependencies.settings.featureDisabled) {
      return;
    }

    initialized = true;
    dependencies.registerDataSourceStubs(uiRouter);
    dependencies.registerStateStubs(applicationState, uiRouter);

    if (dependencies.settings.stagesEnabled !== false) {
      dependencies.registerStage(dependencies.stage);
      dependencies.registerTransformer(dependencies.createStageTransformer());
    }
  };
}

export const initializeKayenta = createKayentaInitializer(initializerDependencies);

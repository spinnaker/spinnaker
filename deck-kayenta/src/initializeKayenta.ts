import type { UIRouter } from '@uirouter/core';
import { registerKayentaDataSourceStubs } from 'kayenta/canary.dataSource.stub';
import 'kayenta/canary.help';
import { CanarySettings } from 'kayenta/canary.settings';
import { registerKayentaStateStubs } from 'kayenta/navigation/canary.states.stub';

import type { ApplicationStateProvider } from '@spinnaker/core';
import { Registry } from '@spinnaker/core';

import { kayentaCanaryStage } from './kayenta/stages/kayentaStage/kayentaStage';
import { KayentaStageTransformer } from './kayenta/stages/kayentaStage/kayentaStage.transformer';

let initialized = false;

export function initializeKayenta(applicationState: ApplicationStateProvider, uiRouter: UIRouter): void {
  if (initialized || CanarySettings.featureDisabled) {
    return;
  }

  initialized = true;
  registerKayentaDataSourceStubs();
  registerKayentaStateStubs(applicationState, uiRouter);

  if (CanarySettings.stagesEnabled !== false) {
    Registry.pipeline.registerStage(kayentaCanaryStage);
    Registry.pipeline.registerTransformer(new KayentaStageTransformer());
  }
}

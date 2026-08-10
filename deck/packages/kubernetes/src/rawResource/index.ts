import { SETTINGS } from '@spinnaker/core';

import { registerKubernetesRawResourceDataSource } from './rawResource.dataSource';
import { registerKubernetesRawResourceStates } from './rawResource.states';

export * from './rawResource.dataSource';
export * from './rawResource.states';

export function registerKubernetesRawResources(): void {
  if (!SETTINGS.feature.kubernetesRawResources) {
    return;
  }

  registerKubernetesRawResourceDataSource();
  registerKubernetesRawResourceStates();
}

registerKubernetesRawResources();

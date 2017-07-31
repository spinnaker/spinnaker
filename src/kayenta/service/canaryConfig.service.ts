import { ReactInjector } from '@spinnaker/core';

import { CanarySettings } from 'kayenta/canary.settings';
import { ICanaryConfigSummary, ICanaryConfig } from 'kayenta/domain/index';
import { ICanaryState } from '../reducers/index';
import { localConfigCache } from './localConfigCache.service';
import {
  ICanaryMetricConfig,
  ICanaryServiceConfig
} from '../domain/ICanaryConfig';

export function getCanaryConfigById(id: string): Promise<ICanaryConfig> {
  if (CanarySettings.liveCalls) {
    return ReactInjector.API.one('v2/canaryConfig').one(id).get();
  } else {
    return localConfigCache.getCanaryConfigById(id);
  }
}

export function getCanaryConfigSummaries(): Promise<ICanaryConfigSummary[]> {
  if (CanarySettings.liveCalls) {
    return ReactInjector.API.one('v2/canaryConfig').get();
  } else {
    return localConfigCache.getCanaryConfigSummaries();
  }
}

export function updateCanaryConfig(config: ICanaryConfig): Promise<{id: string}> {
  if (CanarySettings.liveCalls) {
    return ReactInjector.API.one('v2/canaryConfig').one(config.name).put(config);
  } else {
    return localConfigCache.updateCanaryConfig(config);
  }
}

export function createCanaryConfig(config: ICanaryConfig): Promise<{id: string}> {
  if (CanarySettings.liveCalls) {
    return ReactInjector.API.one('v2/canaryConfig').post(config);
  } else {
    return localConfigCache.createCanaryConfig(config);
  }
}

export function deleteCanaryConfig(id: string): Promise<void> {
  if (CanarySettings.liveCalls) {
    return ReactInjector.API.one('v2/canaryConfig').one(id).remove();
  } else {
    return localConfigCache.deleteCanaryConfig(id);
  }
}

// Not sure if this is the right way to go about this. We have pieces of the config
// living on different parts of the store. Before, e.g., updating the config, we should
// reconstitute it into a single object that reflects the user's changes.
export function mapStateToConfig(state: ICanaryState): ICanaryConfig {
  if (state.selectedConfig) {
    return Object.assign({}, state.selectedConfig, { metrics: state.metricList });
  } else {
    return null;
  }
}

export function buildNewConfig(): ICanaryConfig {
  return {
    name: '(New config)',
    description: '',
    isNew: true,
    metrics: [] as ICanaryMetricConfig[],
    configVersion: '1',
    services: {} as {[key: string]: ICanaryServiceConfig},
    classifier: {
      groupWeights: {} as {[key: string]: number},
      scoreThreshold: {
        pass: 75,
        marginal: 50,
      }
    }
  };
}

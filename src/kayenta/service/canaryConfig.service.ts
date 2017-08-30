import { cloneDeep, set } from 'lodash';
import { ReactInjector } from '@spinnaker/core';

import { CanarySettings } from 'kayenta/canary.settings';
import { ICanaryState } from '../reducers/index';
import { localConfigCache } from './localConfigCache.service';
import {
  ICanaryMetricConfig,
  ICanaryServiceConfig,
  IJudge,
  ICanaryConfigSummary,
  ICanaryConfig
} from '../domain/index';

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

export function listJudges(): Promise<IJudge[]> {
  if (CanarySettings.liveCalls) {
    return ReactInjector.API.one('v2/canaries/judges').get();
  } else {
    return localConfigCache.listJudges();
  }
}

// Not sure if this is the right way to go about this. We have pieces of the config
// living on different parts of the store. Before, e.g., updating the config, we should
// reconstitute it into a single object that reflects the user's changes.
export function mapStateToConfig(state: ICanaryState): ICanaryConfig {
  if (state.selectedConfig.config) {
    const configState = state.selectedConfig;

    const firstMetric = cloneDeep(configState.metricList[0]);
    if (firstMetric && configState.judge) {
      set(firstMetric, 'analysisConfigurations.canary.judge', configState.judge.name);
    }

    return Object.assign({}, configState.config,
      {
        metrics: configState.metricList.map((metric, i) => i === 0 ? firstMetric : metric),
        classifier: Object.assign({}, configState.config.classifier || {}, {
          scoreThresholds: configState.thresholds,
        }),
      }
    );
  } else {
    return null;
  }
}

export function buildNewConfig(state: ICanaryState): ICanaryConfig {
  let configName = 'new-config', i = 1;
  while ((state.data.configSummaries || []).find(summary => summary.name === configName)) {
    configName = `new-config-${i}`;
    i++;
  }

  return {
    name: configName,
    description: '',
    isNew: true,
    metrics: [] as ICanaryMetricConfig[],
    configVersion: '1',
    services: {} as {[key: string]: ICanaryServiceConfig},
    classifier: {
      groupWeights: {} as {[key: string]: number},
      scoreThresholds: {
        pass: 75,
        marginal: 50,
      }
    }
  };
}

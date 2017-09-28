import { omit } from 'lodash';
import { ReactInjector } from '@spinnaker/core';

import { CanarySettings } from 'kayenta/canary.settings';
import { ICanaryState } from '../reducers/index';
import { localConfigCache } from './localConfigCache.service';
import {
  ICanaryMetricConfig,
  IJudge,
  ICanaryConfigSummary,
  ICanaryConfig,
  IKayentaAccount
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
  let allJudges: Promise<IJudge[]>;
  if (CanarySettings.liveCalls) {
    allJudges = ReactInjector.API.one('v2/canaries/judges').get();
  } else {
    allJudges = localConfigCache.listJudges();
  }
  return allJudges.then(judges => judges.filter(judge => judge.visible));
}

export function listKayentaAccounts(): Promise<IKayentaAccount[]> {
  if (CanarySettings.liveCalls) {
    return ReactInjector.API.one('v2/canaries/credentials').get();
  } else {
    return localConfigCache.listKayentaAccounts();
  }
}

// Not sure if this is the right way to go about this. We have pieces of the config
// living on different parts of the store. Before, e.g., updating the config, we should
// reconstitute it into a single object that reflects the user's changes.
export function mapStateToConfig(state: ICanaryState): ICanaryConfig {
  if (state.selectedConfig.config) {
    const configState = state.selectedConfig;
    return {
      ...configState.config,
      judge: configState.judge.judgeConfig,
      metrics: configState.metricList.map(metric => omit(metric, 'id')),
      classifier: {
        ...configState.config.classifier,
        scoreThresholds: configState.thresholds,
        groupWeights: configState.group.groupWeights,
      },
    };
  } else {
    return null;
  }
}

export function buildNewConfig(state: ICanaryState): ICanaryConfig {
  let configName = 'new-config', i = 1;
  while ((state.data.configSummaries || []).some(summary => summary.name === configName)) {
    configName = `new-config-${i}`;
    i++;
  }

  return {
    name: configName,
    description: '',
    isNew: true,
    metrics: [] as ICanaryMetricConfig[],
    configVersion: '1',
    services: {
      [CanarySettings.metricStore]: CanarySettings.defaultServiceSettings[CanarySettings.metricStore]
    },
    classifier: {
      groupWeights: {} as {[key: string]: number},
      scoreThresholds: {
        pass: 75,
        marginal: 50,
      }
    },
    judge: {
      name: CanarySettings.defaultJudge,
      judgeConfigurations: {},
    }
  };
}

export function buildConfigCopy(state: ICanaryState): ICanaryConfig {
  const config = mapStateToConfig(state);
  if (!config) {
    return null;
  }

  // Probably a rare case, but someone could be lazy about naming their configs.
  let configName = `${config.name}-copy`, i = 1;
  while ((state.data.configSummaries || []).some(summary => summary.name === configName)) {
    configName = `${config.name}-copy-${i}`;
    i++;
  }

  return {
    ...config,
    name: configName,
    isNew: true,
  };
}

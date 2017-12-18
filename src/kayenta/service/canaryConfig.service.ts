import { omit } from 'lodash';
import { ReactInjector } from '@spinnaker/core';

import { CanarySettings } from 'kayenta/canary.settings';
import { ICanaryState } from '../reducers/index';
import { localConfigStore } from './localConfigStore.service';
import {
  ICanaryMetricConfig,
  IJudge,
  ICanaryConfigSummary,
  ICanaryConfig,
  IKayentaAccount
} from '../domain/index';
import { ICanaryConfigUpdateResponse } from '../domain/ICanaryConfigUpdateResponse';

export function getCanaryConfigById(id: string): Promise<ICanaryConfig> {
  if (CanarySettings.liveCalls) {
    return ReactInjector.API.one('v2/canaryConfig').one(id).get()
      .then((config: ICanaryConfig) => ({
        ...config,
        id,
      }));
  } else {
    return localConfigStore.getCanaryConfigById(id);
  }
}

export function getCanaryConfigSummaries(...application: string[]): Promise<ICanaryConfigSummary[]> {
  if (CanarySettings.liveCalls) {
    return ReactInjector.API.one('v2/canaryConfig').withParams({ application }).get();
  } else {
    return localConfigStore.getCanaryConfigSummaries();
  }
}

export function updateCanaryConfig(config: ICanaryConfig): Promise<ICanaryConfigUpdateResponse> {
  if (CanarySettings.liveCalls) {
    return ReactInjector.API.one('v2/canaryConfig').one(config.id).put(config);
  } else {
    return localConfigStore.updateCanaryConfig(config);
  }
}

export function createCanaryConfig(config: ICanaryConfig): Promise<ICanaryConfigUpdateResponse> {
  if (CanarySettings.liveCalls) {
    return ReactInjector.API.one('v2/canaryConfig').post(config);
  } else {
    return localConfigStore.createCanaryConfig(config);
  }
}

export function deleteCanaryConfig(id: string): Promise<void> {
  if (CanarySettings.liveCalls) {
    return ReactInjector.API.one('v2/canaryConfig').one(id).remove();
  } else {
    return localConfigStore.deleteCanaryConfig(id);
  }
}

export function listJudges(): Promise<IJudge[]> {
  let allJudges: Promise<IJudge[]>;
  if (CanarySettings.liveCalls) {
    allJudges = ReactInjector.API.one('v2/canaries/judges').get();
  } else {
    allJudges = localConfigStore.listJudges();
  }
  return allJudges.then(judges => judges.filter(judge => judge.visible));
}

export function listKayentaAccounts(): Promise<IKayentaAccount[]> {
  if (CanarySettings.liveCalls) {
    return ReactInjector.API.one('v2/canaries/credentials').get();
  } else {
    return localConfigStore.listKayentaAccounts();
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
    applications: [state.data.application.name],
    description: '',
    isNew: true,
    metrics: [] as ICanaryMetricConfig[],
    configVersion: '1',
    services: {
      [CanarySettings.metricStore]: CanarySettings.defaultServiceSettings[CanarySettings.metricStore]
    },
    templates: {},
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

  return omit({
    ...config,
    name: configName,
    isNew: true,
  }, 'id');
}

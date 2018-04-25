import { omit } from 'lodash';
import { API } from '@spinnaker/core';

import { CanarySettings } from 'kayenta/canary.settings';
import { ICanaryState } from 'kayenta/reducers';
import {
  ICanaryMetricConfig,
  IJudge,
  ICanaryConfigSummary,
  ICanaryConfig,
  IKayentaAccount,
  ICanaryConfigUpdateResponse,
} from 'kayenta/domain';

export function getCanaryConfigById(id: string): Promise<ICanaryConfig> {
  return API.one('v2/canaryConfig').one(id).get()
    .then((config: ICanaryConfig) => ({
      ...config,
      id,
    }));
}

export function getCanaryConfigSummaries(...application: string[]): Promise<ICanaryConfigSummary[]> {
  return API.one('v2/canaryConfig').withParams({ application }).get();
}

export function updateCanaryConfig(config: ICanaryConfig): Promise<ICanaryConfigUpdateResponse> {
  return API.one('v2/canaryConfig').one(config.id).put(config);
}

export function createCanaryConfig(config: ICanaryConfig): Promise<ICanaryConfigUpdateResponse> {
  return API.one('v2/canaryConfig').post(config);
}

export function deleteCanaryConfig(id: string): Promise<void> {
  return API.one('v2/canaryConfig').one(id).remove();
}

export function listJudges(): Promise<IJudge[]> {
  return API.one('v2/canaries/judges').get()
    .then((judges: IJudge[]) => judges.filter(judge => judge.visible));
}

export function listKayentaAccounts(): Promise<IKayentaAccount[]> {
  return API.one('v2/canaries/credentials').useCache().get();
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
  const config: ICanaryConfig = {
    ...mapStateToConfig(state),
    applications: [state.data.application.name], // Copy into current application.
  };
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

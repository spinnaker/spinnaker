import { CanarySettings } from 'kayenta/canary.settings';
import {
  ICanaryConfig,
  ICanaryConfigSummary,
  ICanaryConfigUpdateResponse,
  ICanaryMetricConfig,
  IJudge,
  IKayentaAccount,
} from 'kayenta/domain';
import { ICanaryState } from 'kayenta/reducers';
import { omit } from 'lodash';

import { REST } from '@spinnaker/core';

export function getCanaryConfigById(id: string): PromiseLike<ICanaryConfig> {
  return REST('/v2/canaryConfig')
    .path(id)
    .get()
    .then((config: ICanaryConfig) => ({
      ...config,
      id,
    }));
}

export function getCanaryConfigSummaries(...application: string[]): PromiseLike<ICanaryConfigSummary[]> {
  return REST('/v2/canaryConfig').query({ application }).get();
}

export function updateCanaryConfig(config: ICanaryConfig): PromiseLike<ICanaryConfigUpdateResponse> {
  return REST('/v2/canaryConfig').path(config.id).put(config);
}

export function createCanaryConfig(config: ICanaryConfig): PromiseLike<ICanaryConfigUpdateResponse> {
  return REST('/v2/canaryConfig').post(config);
}

export function deleteCanaryConfig(id: string): PromiseLike<void> {
  return REST('/v2/canaryConfig').path(id).delete();
}

export function listJudges(): PromiseLike<IJudge[]> {
  return REST('/v2/canaries/judges')
    .get()
    .then((judges: IJudge[]) => judges.filter((judge) => judge.visible));
}

export function listKayentaAccounts(): PromiseLike<IKayentaAccount[]> {
  return REST('/v2/canaries/credentials').useCache().get();
}

// Not sure if this is the right way to go about this. We have pieces of the config
// living on different parts of the store. Before, e.g., updating the config, we should
// reconstitute it into a single object that reflects the user's changes.
export function mapStateToConfig(state: ICanaryState): ICanaryConfig {
  const { selectedConfig } = state;
  if (selectedConfig.config) {
    return {
      ...selectedConfig.config,
      judge: selectedConfig.judge.judgeConfig,
      metrics: selectedConfig.metricList.map((metric) => omit(metric, 'id')),
      classifier: {
        ...selectedConfig.config.classifier,
        groupWeights: selectedConfig.group.groupWeights,
      },
    };
  } else {
    return null;
  }
}

export function buildNewConfig(state: ICanaryState): ICanaryConfig {
  let configName = 'new-config';
  let i = 1;
  while ((state.data.configSummaries || []).some((summary) => summary.name === configName)) {
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
      groupWeights: {} as { [key: string]: number },
    },
    judge: {
      name: CanarySettings.defaultJudge || 'NetflixACAJudge-v1.0',
      judgeConfigurations: {},
    },
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
  let configName = `${config.name}-copy`;
  let i = 1;
  while ((state.data.configSummaries || []).some((summary) => summary.name === configName)) {
    configName = `${config.name}-copy-${i}`;
    i++;
  }

  return omit(
    {
      ...config,
      name: configName,
      isNew: true,
    },
    'id',
  );
}

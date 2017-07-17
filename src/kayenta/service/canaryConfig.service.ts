import { ReactInjector } from '@spinnaker/core';

import { CanarySettings } from 'kayenta/canary.settings';
import { ICanaryConfigSummary, ICanaryConfig } from 'kayenta/domain/index';
import { ICanaryState } from '../reducers/index';

const atlasCanaryConfig = require('kayenta/scratch/atlas_canary_config.json');
const stackdriverCanaryConfig = require('kayenta/scratch/stackdriver_canary_config.json');
const canaryConfigSummaries = require('kayenta/scratch/canary_config_summaries.json');

export function getCanaryConfigById(id: string): Promise<ICanaryConfig> {
  if (CanarySettings.liveCalls) {
    return ReactInjector.API.one('v2/canaryConfig').one(id).get();
  } else {
    switch (id) {
      case 'mysampleatlascanaryconfig':
        return Promise.resolve(atlasCanaryConfig);
      case 'mysamplestackdrivercanaryconfig':
        return Promise.resolve(stackdriverCanaryConfig);
      default:
        return Promise.reject('Whoops - this is a fake service!');
    }
  }
}

export function getCanaryConfigSummaries(): Promise<ICanaryConfigSummary[]> {
  if (CanarySettings.liveCalls) {
    return ReactInjector.API.one('v2/canaryConfig').get();
  } else {
    return Promise.resolve(canaryConfigSummaries);
  }
}

export function updateCanaryConfig(config: ICanaryConfig): Promise<string> {
  if (CanarySettings.liveCalls) {
    return ReactInjector.API.one('v2/canaryConfig').put(config);
  } else {
    return Promise.resolve(config.name);
  }
}

export function deleteCanaryConfig(id: string): Promise<void> {
  if (CanarySettings.liveCalls) {
    return ReactInjector.API.one('v2/canaryConfig').one(id).remove();
  } else {
    return Promise.resolve(null);
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

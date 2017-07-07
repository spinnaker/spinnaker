import { ReactInjector } from '@spinnaker/core';

import { CanarySettings } from 'kayenta/canary.settings';
import { ICanaryConfigSummary, ICanaryConfig } from 'kayenta/domain/index';

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

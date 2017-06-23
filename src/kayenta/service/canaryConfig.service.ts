import { module, IQService, IPromise } from 'angular';
import { ICanaryConfigSummary, ICanaryConfig } from 'kayenta/domain/index';

const atlasCanaryConfig = require('kayenta/scratch/atlas_canary_config.json');
const stackdriverCanaryConfig = require('kayenta/scratch/stackdriver_canary_config.json');
const canaryConfigSummaries = require('kayenta/scratch/canary_config_summaries.json');

export class CanaryConfigService {

  constructor(private $q: IQService) {
    'ngInject';
  }

  public getCanaryConfigSummaries(): IPromise<ICanaryConfigSummary[]> {
    return this.$q.resolve(canaryConfigSummaries);
  }

  public getCanaryConfigById(id: string): IPromise<ICanaryConfig> {
    switch (id) {
      case 'mysampleatlascanaryconfig':
        return this.$q.resolve(atlasCanaryConfig);
      case 'mysamplestackdrivercanaryconfig':
        return this.$q.resolve(stackdriverCanaryConfig);
      default:
        throw new Error('Whoops - this is a fake service!');
    }
  }
}

export const CANARY_CONFIG_SERVICE = 'spinnaker.kayenta.canaryConfig.service';
module(CANARY_CONFIG_SERVICE, [])
  .service('canaryConfigService', CanaryConfigService);

import { module, IQService, IPromise } from 'angular';
import { ICanaryConfigSummary, ICanaryConfig } from 'kayenta/domain/index';

const atlasCanaryConfig = require('kayenta/scratch/atlas_canary_config.json');
const stackdriverCanaryConfig = require('kayenta/scratch/stackdriver_canary_config.json');
const canaryConfigSummaries = require('kayenta/scratch/canary_config_summaries.json');

export function getCanaryConfigById(id: string): Promise<ICanaryConfig> {
  switch (id) {
    case 'mysampleatlascanaryconfig':
      return Promise.resolve(atlasCanaryConfig);
    case 'mysamplestackdrivercanaryconfig':
      return Promise.resolve(stackdriverCanaryConfig);
    default:
      return Promise.reject('Whoops - this is a fake service!');
  }
}

export class CanaryConfigService {

  constructor(private $q: IQService) {
    'ngInject';
  }

  public getCanaryConfigSummaries(): IPromise<ICanaryConfigSummary[]> {
    return this.$q.resolve(canaryConfigSummaries);
  }

  public getCanaryConfigById(id: string): IPromise<ICanaryConfig> {
    // the $q.when typedefs only accept IPromise as an input type which ruins it as a conversion tool;
    // thus a cast is necessary
    return this.$q.when(getCanaryConfigById(id)) as IPromise<ICanaryConfig>;
  }
}

export const CANARY_CONFIG_SERVICE = 'spinnaker.kayenta.canaryConfig.service';
module(CANARY_CONFIG_SERVICE, [])
  .service('canaryConfigService', CanaryConfigService);

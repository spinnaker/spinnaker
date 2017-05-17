import { IPromise, IQService, module } from 'angular';

import { IGitTrigger, PIPELINE_CONFIG_PROVIDER } from '@spinnaker/core';

import { NetflixSettings } from 'netflix/netflix.settings';

export class NetflixGitManualExecutionHandler {

  public selectorTemplate: string = require('./selectorTemplate.html');

  constructor(private $q: IQService) { 'ngInject'; }

  public formatLabel(trigger: IGitTrigger): IPromise<string> {
    return this.$q.when(`(${trigger.source}) ${trigger.project}/${trigger.slug}`);
  }
}

export const NETFLIX_GIT_MANUAL_EXECUTION_HANDLER = 'spinnaker.netflix.ci.trigger.handler';
const handlerName = 'netflixCiManualExecutionHandler';
module(NETFLIX_GIT_MANUAL_EXECUTION_HANDLER, [
  PIPELINE_CONFIG_PROVIDER,
])
  .service(handlerName, NetflixGitManualExecutionHandler)
  .run((pipelineConfig: any) => {
    if (NetflixSettings.feature.netflixMode) {
      pipelineConfig.overrideManualExecutionHandler('git', handlerName);
    }
  });

import { IController, module } from 'angular';

import { PIPELINE_CONFIG_PROVIDER, PipelineConfigProvider } from 'core/pipeline/config/pipelineConfigProvider';
import { IPubsubTrigger } from '@spinnaker/core';

class PubsubTriggerController implements IController {
  public pubsubSystems = ['kafka', 'google'];

  constructor(public trigger: IPubsubTrigger) {
    'ngInject';
  }
}

export const PUBSUB_TRIGGER = 'spinnaker.core.pipeline.trigger.pubsub';
module(PUBSUB_TRIGGER, [
  PIPELINE_CONFIG_PROVIDER,
]).config((pipelineConfigProvider: PipelineConfigProvider) => {
  pipelineConfigProvider.registerTrigger({
    label: 'Pub/Sub',
    description: 'Executes the pipeline when a pubsub message is received',
    key: 'pubsub',
    controller: 'PubsubTriggerCtrl',
    controllerAs: 'vm',
    templateUrl: require('./pubsubTrigger.html'),
    validators: []
  });
}).controller('PubsubTriggerCtrl', PubsubTriggerController);

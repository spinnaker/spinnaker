import { IController, module } from 'angular';

import { SETTINGS } from 'core/config/settings';

import { PIPELINE_CONFIG_PROVIDER, PipelineConfigProvider } from 'core/pipeline/config/pipelineConfigProvider';
import {
  PUBSUB_SUBSCRIPTION_SERVICE,
  PubsubSubscriptionService,
} from 'core/pubsub';
import {
  IPubsubTrigger,
} from 'core/domain';

class PubsubTriggerController implements IController {
  public pubsubSystems = SETTINGS.pubsubProviders || ['google', 'kafka'];
  public pubsubSubscriptions: string[];
  public subscriptionsLoaded = false;

  constructor(public trigger: IPubsubTrigger,
              pubsubSubscriptionService: PubsubSubscriptionService) {
    'ngInject';

    this.subscriptionsLoaded = false;
    pubsubSubscriptionService.getPubsubSubscriptions()
      .then(subscriptions => this.pubsubSubscriptions = subscriptions)
      .catch(() => this.pubsubSubscriptions = [])
      .finally(() => this.subscriptionsLoaded = true);
  }
}

export const PUBSUB_TRIGGER = 'spinnaker.core.pipeline.trigger.pubsub';
module(PUBSUB_TRIGGER, [
  PIPELINE_CONFIG_PROVIDER,
  PUBSUB_SUBSCRIPTION_SERVICE,
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

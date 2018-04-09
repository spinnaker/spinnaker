import { IController, module } from 'angular';

import { PIPELINE_CONFIG_PROVIDER, PipelineConfigProvider } from 'core/pipeline/config/pipelineConfigProvider';
import { SETTINGS } from 'core/config/settings';
import { IWebhookTrigger } from 'core/domain';

class WebhookTriggerController implements IController {
  constructor(public trigger: IWebhookTrigger) {
    'ngInject';
  }

  public getTriggerEndpoint(): string {
    return `${SETTINGS.gateUrl}/webhooks/${this.trigger.type}/${this.trigger.source || '<source>'}`;
  }
}

export const WEBHOOK_TRIGGER = 'spinnaker.core.pipeline.trigger.webhook';
module(WEBHOOK_TRIGGER, [PIPELINE_CONFIG_PROVIDER])
  .config((pipelineConfigProvider: PipelineConfigProvider) => {
    pipelineConfigProvider.registerTrigger({
      label: 'Webhook',
      description: 'Executes the pipeline when a webhook is received.',
      key: 'webhook',
      controller: 'WebhookTriggerCtrl',
      controllerAs: 'ctrl',
      templateUrl: require('./webhookTrigger.html'),
      validators: [],
    });
  })
  .controller('WebhookTriggerCtrl', WebhookTriggerController);

import { IController, module } from 'angular';

import { IWebhookTrigger } from 'core/domain';
import { Registry } from 'core/registry';
import { SETTINGS } from 'core/config/settings';

class WebhookTriggerController implements IController {
  constructor(public trigger: IWebhookTrigger) {
    'ngInject';
  }

  public getTriggerEndpoint(): string {
    return `${SETTINGS.gateUrl}/webhooks/${this.trigger.type}/${this.trigger.source || '<source>'}`;
  }
}

export const WEBHOOK_TRIGGER = 'spinnaker.core.pipeline.trigger.webhook';
module(WEBHOOK_TRIGGER, [])
  .config(() => {
    Registry.pipeline.registerTrigger({
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

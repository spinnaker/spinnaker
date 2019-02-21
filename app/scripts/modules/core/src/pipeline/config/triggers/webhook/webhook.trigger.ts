import { IController, module } from 'angular';

import { IWebhookTrigger } from 'core/domain';
import { Registry } from 'core/registry';
import { ServiceAccountReader } from 'core/serviceAccount/ServiceAccountReader';
import { SETTINGS } from 'core/config/settings';

class WebhookTriggerController implements IController {
  public fiatEnabled: boolean;
  public serviceAccounts: string[];

  public static $inject = ['trigger'];
  constructor(public trigger: IWebhookTrigger) {
    this.fiatEnabled = SETTINGS.feature.fiatEnabled;
    ServiceAccountReader.getServiceAccounts().then(accounts => {
      this.serviceAccounts = accounts || [];
    });
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
      validators: [
        {
          type: 'serviceAccountAccess',
          message: `You do not have access to the service account configured in this pipeline's webhook trigger.
                    You will not be able to save your edits to this pipeline.`,
          preventSave: true,
        },
      ],
    });
  })
  .controller('WebhookTriggerCtrl', WebhookTriggerController);

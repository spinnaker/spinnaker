import { webhookExecutionDetailsSections } from './WebhookExecutionDetails';
import { WebhookStageConfig } from './WebhookStageConfig';
import type { IPreconfiguredWebhook } from './WebhookStageConfig';
import { REST } from '../../../../api/ApiService';
import type { IStageTypeConfig } from '../../../../domain';
import { Registry } from '../../../../registry';

export { WebhookStageConfig } from './WebhookStageConfig';
export type {
  ICustomHeader,
  IPreconfiguredWebhook,
  IWebhookParameter,
  IWebhookStageCommand,
  IWebhookStageViewState,
} from './WebhookStageConfig';

export const webhookStage: IStageTypeConfig = {
  label: 'Webhook',
  description: 'Runs a Webhook job',
  key: 'webhook',
  restartable: true,
  producesArtifacts: true,
  component: WebhookStageConfig,
  executionDetailsSections: webhookExecutionDetailsSections,
  supportsCustomTimeout: true,
  validators: [
    { type: 'requiredField', fieldName: 'url' },
    { type: 'requiredField', fieldName: 'method' },
  ],
};

export function makePreconfiguredWebhookStage(preconfiguredWebhook: IPreconfiguredWebhook): IStageTypeConfig {
  return {
    label: preconfiguredWebhook.label,
    description: preconfiguredWebhook.description,
    key: preconfiguredWebhook.type,
    alias: 'preconfiguredWebhook',
    addAliasToConfig: true,
    producesArtifacts: true,
    restartable: true,
    component: WebhookStageConfig,
    executionDetailsSections: webhookExecutionDetailsSections,
    validators: [],
    configuration: {
      preconfiguredProperties: preconfiguredWebhook.preconfiguredProperties,
      waitForCompletion: preconfiguredWebhook.waitForCompletion,
      noUserConfigurableFields: preconfiguredWebhook.noUserConfigurableFields,
      parameters: preconfiguredWebhook.parameters,
    },
  };
}

export function registerPreconfiguredWebhookStages(): Promise<void> {
  return REST('/webhooks/preconfigured')
    .get<IPreconfiguredWebhook[]>()
    .then((preconfiguredWebhooks) => {
      preconfiguredWebhooks.forEach((preconfiguredWebhook) =>
        Registry.pipeline.registerStage(makePreconfiguredWebhookStage(preconfiguredWebhook)),
      );
    });
}

Registry.pipeline.registerStage(webhookStage);

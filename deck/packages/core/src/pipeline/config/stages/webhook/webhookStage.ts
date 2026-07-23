import type { IController } from 'angular';
import { module } from 'angular';
import type { IModalService } from 'angular-ui-bootstrap';

import { webhookExecutionDetailsSections } from './WebhookExecutionDetails';
import { WebhookStageConfig } from './WebhookStageConfig';
import type {
  ICustomHeader,
  IPreconfiguredWebhook,
  IWebhookParameter,
  IWebhookStageCommand,
  IWebhookStageViewState,
} from './WebhookStageConfig';
import { REST } from '../../../../api/ApiService';
import type { IStageTypeConfig } from '../../../../domain';
import { Registry } from '../../../../registry';
import { JsonUtils } from '../../../../utils';

export { WebhookStageConfig } from './WebhookStageConfig';
export type {
  ICustomHeader,
  IPreconfiguredWebhook,
  IWebhookParameter,
  IWebhookStageCommand,
  IWebhookStageViewState,
} from './WebhookStageConfig';

export class WebhookStage implements IController {
  public command: IWebhookStageCommand;
  public cancelCommand: IWebhookStageCommand;
  public viewState: IWebhookStageViewState;
  public methods: string[];
  public preconfiguredProperties: string[];
  public noUserConfigurableFields: boolean;
  public parameters: IWebhookParameter[] = [];

  public static $inject = ['stage', '$uibModal'];
  constructor(public stage: any, private $uibModal: IModalService) {
    this.methods = ['GET', 'HEAD', 'POST', 'PUT', 'PATCH', 'DELETE'];

    this.viewState = {
      waitForCompletion: this.stage.waitForCompletion || false,
      statusUrlResolution: this.stage.statusUrlResolution || 'getMethod',
      failFastStatusCodes: this.stage.failFastStatusCodes ? this.stage.failFastStatusCodes.join() : '',
      retryStatusCodes: this.stage.retryStatusCodes ? this.stage.retryStatusCodes.join() : '',
    };

    this.command = {
      payloadJSON: JsonUtils.makeSortedStringFromObject(this.stage.payload || {}),
    };

    this.cancelCommand = {
      payloadJSON: JsonUtils.makeSortedStringFromObject(this.stage.cancelPayload || {}),
    };

    this.stage.statusUrlResolution = this.viewState.statusUrlResolution;

    const stageConfig = Registry.pipeline.getStageConfig(this.stage);
    if (stageConfig && stageConfig.configuration) {
      this.preconfiguredProperties = stageConfig.configuration.preconfiguredProperties || [];
      this.noUserConfigurableFields = stageConfig.configuration.noUserConfigurableFields;
      this.viewState.waitForCompletion =
        stageConfig.configuration.waitForCompletion || this.viewState.waitForCompletion;
      this.parameters = stageConfig.configuration.parameters || [];
      this.viewState.failFastStatusCodes = this.stage.failFastStatusCodes ? this.stage.failFastStatusCodes.join() : '';
      this.viewState.retryStatusCodes = this.stage.retryStatusCodes ? this.stage.retryStatusCodes.join() : '';
    }

    if (this.parameters.length && !this.stage.parameterValues) {
      this.stage.parameterValues = {};
    }

    this.parameters.forEach((config: any) => {
      if (!(config.name in this.stage.parameterValues) && config.defaultValue !== null) {
        this.stage.parameterValues[config.name] = config.defaultValue;
      }
    });
  }

  public updatePayload(): void {
    const payload = WebhookStage.checkAndGetPayload(this.command);

    if (payload !== undefined) {
      this.stage.payload = payload;
    }
  }

  public updateCancelPayload(): void {
    const payload = WebhookStage.checkAndGetPayload(this.cancelCommand);

    if (payload !== undefined) {
      this.stage.cancelPayload = payload;
    }
  }

  private static checkAndGetPayload(command: IWebhookStageCommand): void {
    command.invalid = false;
    command.errorMessage = '';

    try {
      return command.payloadJSON ? JSON.parse(command.payloadJSON) : null;
    } catch (e) {
      command.invalid = true;
      command.errorMessage = e.message;
    }

    return undefined;
  }

  public waitForCompletionChanged(): void {
    this.stage.waitForCompletion = this.viewState.waitForCompletion;
  }

  public signalCancellationChanged(): void {
    if (!this.viewState.signalCancellation) {
      // Reset the data to "defaults" when user disables this option
      delete this.stage.cancelEndpoint;
      delete this.stage.cancelMethod;
      delete this.stage.cancelPayload;

      this.cancelCommand = {
        payloadJSON: JsonUtils.makeSortedStringFromObject(this.stage.cancelPayload || {}),
      };
    } else {
      this.stage.cancelMethod = 'POST';
    }
  }

  public statusUrlResolutionChanged(): void {
    this.stage.statusUrlResolution = this.viewState.statusUrlResolution;
  }

  public failFastCodesChanged(): void {
    const failFastCodes = this.viewState.failFastStatusCodes.split(',').map((x) => x.trim());

    this.stage.failFastStatusCodes = failFastCodes.map((x) => parseInt(x, 10)).filter((x) => !isNaN(x));
  }

  public retryCodesChanged(): void {
    const retryCodes = this.viewState.retryStatusCodes.split(',').map((x) => x.trim());

    this.stage.retryStatusCodes = retryCodes.map((x) => parseInt(x, 10)).filter((x) => !isNaN(x));
  }

  public customHeaderCount(): number {
    return this.stage.customHeaders ? Object.keys(this.stage.customHeaders).length : 0;
  }

  public removeCustomHeader(key: string): void {
    delete this.stage.customHeaders[key];
  }

  public addCustomHeader(): void {
    if (!this.stage.customHeaders) {
      this.stage.customHeaders = {};
    }
    this.$uibModal
      .open({
        templateUrl: require('./modal/addCustomHeader.html'),
        controller: 'WebhookStageAddCustomHeaderCtrl',
        controllerAs: 'addCustomHeader',
      })
      .result.then((customHeader: ICustomHeader) => {
        this.stage.customHeaders[customHeader.key] = customHeader.value;
      })
      .catch(() => {});
  }

  public displayField(field: string): boolean {
    return !this.preconfiguredProperties || !this.preconfiguredProperties.some((property) => property === field);
  }
}

export const WEBHOOK_STAGE = 'spinnaker.core.pipeline.stage.webhookStage';

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

export function registerPreconfiguredWebhookStages(): PromiseLike<void> {
  return REST('/webhooks/preconfigured')
    .get<IPreconfiguredWebhook[]>()
    .then((preconfiguredWebhooks) => {
      preconfiguredWebhooks.forEach((preconfiguredWebhook) =>
        Registry.pipeline.registerStage(makePreconfiguredWebhookStage(preconfiguredWebhook)),
      );
    });
}

Registry.pipeline.registerStage(webhookStage);

module(WEBHOOK_STAGE, [])
  .run(() => registerPreconfiguredWebhookStages())
  .controller('WebhookStageCtrl', WebhookStage);

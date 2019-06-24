import { IController, module } from 'angular';
import { IModalService } from 'angular-ui-bootstrap';

import { API } from 'core/api/ApiService';
import { JsonUtils } from 'core/utils';
import { Registry } from 'core/registry';

export interface IWebhookStageViewState {
  waitForCompletion?: boolean;
  statusUrlResolution: string;
  failFastStatusCodes: string;
  retryStatusCodes: string;
}

export interface IWebhookStageCommand {
  errorMessage?: string;
  invalid?: boolean;
  payloadJSON: string;
}

export interface ICustomHeader {
  key: string;
  value: string;
}

export interface IWebhookParameter {
  name: string;
  label: string;
  description?: string;
  type: string;
  defaultValue?: string;
}

interface IPreconfiguredWebhook {
  type: string;
  label: string;
  noUserConfigurableFields: boolean;
  description?: string;
  waitForCompletion?: boolean;
  preconfiguredProperties?: string[];
  parameters?: IWebhookParameter[];
}

export class WebhookStage implements IController {
  public command: IWebhookStageCommand;
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
    this.command.invalid = false;
    this.command.errorMessage = '';
    try {
      this.stage.payload = this.command.payloadJSON ? JSON.parse(this.command.payloadJSON) : null;
    } catch (e) {
      this.command.invalid = true;
      this.command.errorMessage = e.message;
    }
  }

  public waitForCompletionChanged(): void {
    this.stage.waitForCompletion = this.viewState.waitForCompletion;
  }

  public statusUrlResolutionChanged(): void {
    this.stage.statusUrlResolution = this.viewState.statusUrlResolution;
  }

  public failFastCodesChanged(): void {
    const failFastCodes = this.viewState.failFastStatusCodes.split(',').map(x => x.trim());

    this.stage.failFastStatusCodes = failFastCodes.map(x => parseInt(x, 10)).filter(x => !isNaN(x));
  }

  public retryCodesChanged(): void {
    const retryCodes = this.viewState.retryStatusCodes.split(',').map(x => x.trim());

    this.stage.retryStatusCodes = retryCodes.map(x => parseInt(x, 10)).filter(x => !isNaN(x));
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
    return !this.preconfiguredProperties || !this.preconfiguredProperties.some(property => property === field);
  }
}

export const WEBHOOK_STAGE = 'spinnaker.core.pipeline.stage.webhookStage';

module(WEBHOOK_STAGE, [])
  .config(() => {
    Registry.pipeline.registerStage({
      label: 'Webhook',
      description: 'Runs a Webhook job',
      key: 'webhook',
      restartable: true,
      controller: 'WebhookStageCtrl',
      producesArtifacts: true,
      controllerAs: '$ctrl',
      templateUrl: require('./webhookStage.html'),
      executionDetailsUrl: require('./webhookExecutionDetails.html'),
      defaultTimeoutMs: 60 * 60 * 1000, // 1 hour
      validators: [{ type: 'requiredField', fieldName: 'url' }, { type: 'requiredField', fieldName: 'method' }],
    });
  })
  .run(() => {
    API.one('webhooks')
      .all('preconfigured')
      .getList()
      .then((preconfiguredWebhooks: IPreconfiguredWebhook[]) => {
        preconfiguredWebhooks.forEach((preconfiguredWebhook: IPreconfiguredWebhook) =>
          Registry.pipeline.registerStage({
            label: preconfiguredWebhook.label,
            description: preconfiguredWebhook.description,
            key: preconfiguredWebhook.type,
            alias: 'preconfiguredWebhook',
            addAliasToConfig: true,
            producesArtifacts: true,
            restartable: true,
            controller: 'WebhookStageCtrl',
            controllerAs: '$ctrl',
            templateUrl: require('./webhookStage.html'),
            executionDetailsUrl: require('./webhookExecutionDetails.html'),
            validators: [],
            configuration: {
              preconfiguredProperties: preconfiguredWebhook.preconfiguredProperties,
              waitForCompletion: preconfiguredWebhook.waitForCompletion,
              noUserConfigurableFields: preconfiguredWebhook.noUserConfigurableFields,
              parameters: preconfiguredWebhook.parameters,
            },
          }),
        );
      });
  })
  .controller('WebhookStageCtrl', WebhookStage);

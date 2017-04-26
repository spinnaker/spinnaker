import {module} from 'angular';

import {JSON_UTILITY_SERVICE, JsonUtilityService} from 'core/utils/json/json.utility.service';
import {PIPELINE_CONFIG_PROVIDER} from 'core/pipeline/config/pipelineConfigProvider';
import {IModalService} from 'angular-ui-bootstrap';

interface IViewState {
  waitForCompletion?: boolean;
  statusUrlResolution: string;
}

interface ICommand {
  errorMessage?: string;
  invalid?: boolean;
  payloadJSON: string;
}

export interface ICustomHeader {
  key: string;
  value: string;
}

export class WebhookStage {
  static get $inject() {
    return ['stage', 'jsonUtilityService', '$uibModal'];
  }

  public command: ICommand;
  public viewState: IViewState;
  public methods: string[];

  constructor(public stage: any,
              private jsonUtilityService: JsonUtilityService,
              private $uibModal: IModalService) {
    this.methods = ['GET', 'HEAD', 'POST', 'PUT', 'DELETE'];

    this.viewState = {
      waitForCompletion: this.stage.waitForCompletion || false,
      statusUrlResolution: this.stage.statusUrlResolution || 'getMethod'
    };

    this.command = {
      payloadJSON: this.jsonUtilityService.makeSortedStringFromObject(this.stage.payload || {}),
    };
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

  public removeCustomHeader(key: string): void {
    delete this.stage.customHeaders[key];
  }

  public addCustomHeader(): void {
    if (!this.stage.customHeaders) {
      this.stage.customHeaders = {};
    }
    this.$uibModal.open({
      templateUrl: require('core/pipeline/config/stages/webhook/modal/addCustomHeader.html'),
      controller: 'WebhookStageAddCustomHeaderCtrl',
      controllerAs: 'addCustomHeader',
    }).result.then((customHeader: ICustomHeader) => {
      this.stage.customHeaders[customHeader.key] = customHeader.value;
    });
  }
}

export const WEBHOOK_STAGE = 'spinnaker.core.pipeline.stage.webhookStage';

module(WEBHOOK_STAGE, [
  JSON_UTILITY_SERVICE,
  PIPELINE_CONFIG_PROVIDER
]).config((pipelineConfigProvider: any) => {
  pipelineConfigProvider.registerStage({
    label: 'Webhook',
    description: 'Runs a Webhook job',
    key: 'webhook',
    restartable: true,
    controller: 'WebhookStageCtrl',
    controllerAs: '$ctrl',
    templateUrl: require('./webhookStage.html'),
    executionDetailsUrl: require('./webhookExecutionDetails.html'),
    validators: [
      {type: 'requiredField', fieldName: 'url'},
    ],
    strategy: true,
  });
}).controller('WebhookStageCtrl', WebhookStage);

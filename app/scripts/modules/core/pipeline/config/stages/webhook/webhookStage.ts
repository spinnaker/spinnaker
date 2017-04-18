import {module, extend} from 'angular';

import {JSON_UTILITY_SERVICE, JsonUtilityService} from 'core/utils/json/json.utility.service';
import {PIPELINE_CONFIG_PROVIDER} from 'core/pipeline/config/pipelineConfigProvider';

interface IViewState {
  waitForCompletion?: boolean;
  statusUrlResolution: string;
}

interface ICommand {
  errorMessage?: string;
  invalid?: boolean;
  payloadJSON: string;
}

export class WebhookStage {
  static get $inject() {
    return ['stage', 'jsonUtilityService'];
  }

  public command: ICommand;
  public viewState: IViewState;
  public methods: string[];

  constructor(public stage: any,
              private jsonUtilityService: JsonUtilityService) {
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
      const parsed = JSON.parse(this.command.payloadJSON);
      if (!this.stage.payload) {
        this.stage.payload = {};
      }
      extend(this.stage.payload, parsed);
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

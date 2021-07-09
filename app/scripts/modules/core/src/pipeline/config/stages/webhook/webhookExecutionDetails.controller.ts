import { StateParams } from '@uirouter/angularjs';
import { IController, IScope, module } from 'angular';
import { get } from 'lodash';

import { EXECUTION_ARTIFACT_TAB } from '../../../../artifact/artifactTab';

import {
  EXECUTION_DETAILS_SECTION_SERVICE,
  ExecutionDetailsSectionService,
} from '../../../details/executionDetailsSection.service';

export class WebhookExecutionDetailsCtrl implements IController {
  public configSections = ['webhookConfig', 'taskStatus', 'artifactStatus'];
  public detailsSection: string;
  public failureMessage: string;
  public progressMessage: string;
  public body: string;
  public stage: any;
  public payload: string;

  public static $inject = ['$stateParams', 'executionDetailsSectionService', '$scope'];
  constructor(
    private $stateParams: StateParams,
    private executionDetailsSectionService: ExecutionDetailsSectionService,
    private $scope: IScope,
  ) {
    this.initialize();
    this.$scope.$on('$stateChangeSuccess', () => this.initialize());
  }

  public initialized(): void {
    this.stage = this.$scope.stage;
    this.detailsSection = get<string>(this.$stateParams, 'details', '');
    this.failureMessage = this.getFailureMessage();
    this.progressMessage = this.getProgressMessage();
    this.body = this.getBodyContent();
    this.payload = JSON.stringify(this.stage.context.payload, null, 2);
  }

  private getProgressMessage(): string {
    const context = this.stage.context || {};
    const webhook = context.webhook || {};
    const monitor = webhook.monitor || {};
    const buildInfo = context.buildInfo || {};

    if (monitor.progressMessage) {
      return monitor.progressMessage;
    } else {
      return buildInfo.progressMessage;
    }
  }

  private getFailureMessage(): string {
    let failureMessage = this.stage.failureMessage;
    const context = this.stage.context || {};
    const webhook = context.webhook || {};
    const monitor = webhook.monitor || {};
    const error = monitor.error || webhook.error || null;

    if (error) {
      failureMessage = `Webhook failed: ${error}`;
    }

    return failureMessage;
  }

  private getBodyContent(): string {
    // If there was a webhook monitor task get the body from it, otherwise get it from webhook
    const context = this.stage.context || {};
    const webhook = context.webhook || {};
    const monitor = webhook.monitor || {};

    const body = monitor.body || webhook.body || null;

    // Empty body is only allowed when we haven't started or are running the task.
    // Otherwise, assume the request completed and didn't yield a body in the response
    if (!body && this.stage.originalStatus !== 'NOT_STARTED' && this.stage.originalStatus !== 'RUNNING') {
      return '<NO BODY RETURNED BY SERVER>';
    }

    if (typeof body === 'object') {
      return JSON.stringify(body, null, 2);
    }

    return body;
  }

  private initialize(): void {
    this.executionDetailsSectionService.synchronizeSection(this.configSections, () => this.initialized());
  }
}

export const WEBHOOK_EXECUTION_DETAILS_CONTROLLER = 'spinnaker.core.pipeline.stage.webhook.executionDetails.controller';
module(WEBHOOK_EXECUTION_DETAILS_CONTROLLER, [EXECUTION_DETAILS_SECTION_SERVICE, EXECUTION_ARTIFACT_TAB]).controller(
  'WebhookExecutionDetailsCtrl',
  WebhookExecutionDetailsCtrl,
);

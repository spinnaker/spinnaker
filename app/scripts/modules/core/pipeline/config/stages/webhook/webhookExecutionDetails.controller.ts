import {module, IScope} from 'angular';
import {StateParams} from 'angular-ui-router';
import {get} from 'lodash';

import {
  EXECUTION_DETAILS_SECTION_SERVICE,
  ExecutionDetailsSectionService
} from 'core/delivery/details/executionDetailsSection.service';

export class WebhookExecutionDetailsCtrl {
  public configSections = ['webhookConfig', 'taskStatus'];
  public detailsSection: string;
  public failureMessage: string;
  public progressMessage: string;
  public stage: any;

  constructor(private $stateParams: StateParams,
              private executionDetailsSectionService: ExecutionDetailsSectionService,
              private $scope: IScope) {
    'ngInject';
    this.stage = this.$scope.stage;
    this.initialize();
    this.$scope.$on('$stateChangeSuccess', () => this.initialize());
  }

  public initialized(): void {
    this.detailsSection = get<string>(this.$stateParams, 'details', '');
    this.failureMessage = this.getFailureMessage();
    this.progressMessage = this.getProgressMessage();
  }

  private getProgressMessage(): string {
    const context = this.stage.context || {},
      buildInfo = context.buildInfo || {};
    return buildInfo.progressMessage;
  }

  private getFailureMessage(): string {
    let failureMessage = this.stage.failureMessage;
    const context = this.stage.context || {},
      buildInfo = context.buildInfo || {};
    if (buildInfo.status === 'TERMINAL') {
        failureMessage = `Webhook failed: ${buildInfo.reason}`;
    }
    return failureMessage;
  }

  private initialize(): void {
    this.executionDetailsSectionService.synchronizeSection(this.configSections, () => this.initialized());
  }
}

export const WEBHOOK_EXECUTION_DETAILS_CONTROLLER = 'spinnaker.core.pipeline.stage.webhook.executionDetails.controller';
module(WEBHOOK_EXECUTION_DETAILS_CONTROLLER, [
  EXECUTION_DETAILS_SECTION_SERVICE,
  require('core/delivery/details/executionDetailsSectionNav.directive.js'),
]).controller('WebhookExecutionDetailsCtrl', WebhookExecutionDetailsCtrl);

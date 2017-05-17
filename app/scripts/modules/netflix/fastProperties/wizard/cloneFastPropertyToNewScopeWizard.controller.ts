import { IScope, module } from 'angular';
import { IModalServiceInstance } from 'angular-ui-bootstrap';
import { RawParams, StateService } from 'angular-ui-router';
import { has } from 'lodash';

import { Application, IExecution, PIPELINE_CONFIG_SERVICE, PipelineConfigService } from '@spinnaker/core';

import { IPlatformProperty } from '../domain/platformProperty.model';
import { PropertyCommand } from '../domain/propertyCommand.model';
import { PropertyCommandType } from '../domain/propertyCommandType.enum';
import { PropertyPipeline } from '../domain/propertyPipeline.domain';
import { FastPropertyReaderService } from '../fastProperty.read.service';
import { FAST_PROPERTY_DETAILS_COMPONENT } from './propertyDetails/propertyDetails.component';
import { FAST_PROPERTY_REVIEW_COMPONENT } from './propertyReview/propertyReview.component';
import { FAST_PROPERTY_SCOPE_COMPONENT } from './propertyScope/propertyScope.component';
import { FAST_PROPERTY_STRATEGY_COMPONENT } from './propertyStrategy/propertyStrategy.component';

interface IState {
  submitting: boolean;
}

class CloneFastPropertyToNewScopeWizardController {

  public state: IState;
  public command: PropertyCommand = new PropertyCommand();

  constructor (
    public $scope: IScope,
    private $uibModalInstance: IModalServiceInstance,
    public title: string,
    public property: IPlatformProperty,
    public application: Application,
    public pipelineConfigService: PipelineConfigService,
    public fastPropertyReader: FastPropertyReaderService,
    private $state: StateService) {
      'ngInject';
      this.command.type = PropertyCommandType.CREATE;
      this.command.applicationName = this.application ? this.application.name : 'Global';
      this.command.buildPropertyWithoutId(property);
      this.state = {
        submitting: false
      };
  }

  public showSubmitButton(): boolean {
    return !!this.command.pipeline;
  }

  public isValid(): boolean {
    return !!this.command.pipeline && !!this.command.property.isValid() && this.command.scopes.length > 0;
  }

  public cancel(): void {
    this.$uibModalInstance.dismiss();
  }

  public startPipeline(pipeline: PropertyPipeline): void {
    this.state.submitting = true;
    const propertyFound = (e: IExecution) => has(e, 'context.persistedProperties[0].propertyId');
    this.pipelineConfigService.startAdHocPipeline(pipeline).then((executionId) => {
      this.fastPropertyReader.waitForPromotionPipelineToAppear(this.application, executionId, propertyFound)
        .then((execution: IExecution) => {
          this.$state.go(this.getNextState(), this.getNextParams(execution));
        });
    });
  }

  private getNextParams(execution: IExecution): RawParams {
    const propertyId = execution.context.persistedProperties[0].propertyId;
    const nextParams: RawParams = { propertyId };
    if (this.command.strategy.isForcePush()) {
      return nextParams;
    }
    nextParams.executionId = execution.id;
    if (!this.application.global) {
      nextParams.tab = 'rollouts';
    }
    return nextParams;
  }

  private getNextState(): string {
    if (this.command.strategy.isForcePush()) {
      return '.';
    }
    let nextState = this.$state.current.name.endsWith('.execution') ? '.' : '.execution';
    if (this.application.global) {
      nextState = '^.rollouts.execution';
    }
    return nextState;
  }

}

export const CLONE_FAST_PROPERTY_TO_NEW_SCOPE_WIZARD_CONTROLLER = 'spinnaker.netflix.fastProperty.clonePropertyToNewScope.controller';

module(CLONE_FAST_PROPERTY_TO_NEW_SCOPE_WIZARD_CONTROLLER, [
  FAST_PROPERTY_DETAILS_COMPONENT,
  FAST_PROPERTY_SCOPE_COMPONENT,
  FAST_PROPERTY_STRATEGY_COMPONENT,
  FAST_PROPERTY_REVIEW_COMPONENT,
  PIPELINE_CONFIG_SERVICE,
])
  .controller('cloneFastPropertyToNewScopeWizardController', CloneFastPropertyToNewScopeWizardController);


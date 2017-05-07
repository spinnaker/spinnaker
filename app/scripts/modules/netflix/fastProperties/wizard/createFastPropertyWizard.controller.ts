import { module, IScope } from 'angular';
import { StateService } from 'angular-ui-router';
import { IModalServiceInstance } from 'angular-ui-bootstrap';

import { FAST_PROPERTY_DETAILS_COMPONENT } from './propertyDetails/propertyDetails.component';
import { FAST_PROPERTY_SCOPE_COMPONENT } from './propertyScope/propertyScope.component';
import { FAST_PROPERTY_REVIEW_COMPONENT } from './propertyReview/propertyReview.component';
import { FAST_PROPERTY_STRATEGY_COMPONENT } from './propertyStrategy/propertyStrategy.component';
import { PIPELINE_CONFIG_SERVICE, PipelineConfigService } from 'core/pipeline/config/services/pipelineConfig.service';
import { PropertyCommandType }from '../domain/propertyCommandType.enum';
import { PropertyPipeline } from '../domain/propertyPipeline.domain';
import { PropertyCommand } from '../domain/propertyCommand.model';
import { Application } from 'core/application/application.model';
import { FastPropertyReaderService } from '../fastProperty.read.service';
import { IExecution } from '../../../core/domain/IExecution';

interface IState {
  submitting: boolean;
}

class CreateFastPropertyWizardController {

  public command: PropertyCommand = new PropertyCommand();
  public state: IState;

  constructor (
    public $scope: IScope,
    public $uibModalInstance: IModalServiceInstance,
    public title: string,
    public application: Application,
    public pipelineConfigService: PipelineConfigService,
    public fastPropertyReader: FastPropertyReaderService,
    public $state: StateService) {
      'ngInject';
      this.state = {
        submitting: false
      };

      this.command.type = PropertyCommandType.CREATE;
      this.command.applicationName = this.application.name;
  }

  public showSubmitButton(): boolean {
    return !!this.command.pipeline;
  }

  public startPipeline(pipeline: PropertyPipeline): void {
    this.state.submitting = true;
    this.pipelineConfigService.startAdHocPipeline(pipeline).then((executionId) => {
      this.fastPropertyReader.waitForPromotionPipelineToAppear(this.application, executionId)
        .then((execution: IExecution) => {
          const propertyId = execution.context.persistedProperties[0].propertyId;
          let nextState = this.$state.current.name.endsWith('.execution') ? '.' : '.execution';
          if (this.application.global) {
            nextState = '^.rollouts.execution';
          }
          this.$state.go(nextState, { executionId, propertyId });
          this.$uibModalInstance.close();
        });
    });
  }


  public isValid(): boolean {
    return !!this.command.pipeline && !!this.command.property.isValid() && this.command.scopes.length > 0;
  }

  public cancel(): void {
    this.$uibModalInstance.dismiss();
  }

}

export const CREATE_FAST_PROPERTY_WIZARD_CONTROLLER = 'spinnaker.netflix.fastProperty.createWizard.controller';

module(CREATE_FAST_PROPERTY_WIZARD_CONTROLLER, [
  FAST_PROPERTY_DETAILS_COMPONENT,
  FAST_PROPERTY_SCOPE_COMPONENT,
  FAST_PROPERTY_STRATEGY_COMPONENT,
  FAST_PROPERTY_REVIEW_COMPONENT,
  PIPELINE_CONFIG_SERVICE,
])
  .controller('createFastPropertyWizardController', CreateFastPropertyWizardController);

import { module, IScope } from 'angular';
import { StateService, RawParams } from 'angular-ui-router';
import { IModalServiceInstance } from 'angular-ui-bootstrap';

import { FAST_PROPERTY_DETAILS_COMPONENT } from './propertyDetails/propertyDetails.component';
import { FAST_PROPERTY_SCOPE_READ_ONLY_COMPONENT } from './propertyScope/propertyScopeReadOnly.component';
import { FAST_PROPERTY_REVIEW_COMPONENT } from './propertyReview/propertyReview.component';
import { FAST_PROPERTY_STRATEGY_COMPONENT } from './propertyStrategy/propertyStrategy.component';
import { PropertyCommandType }from '../domain/propertyCommandType.enum';
import { PropertyPipeline } from '../domain/propertyPipeline.domain';
import { APPLICATION_READ_SERVICE } from 'core/application/service/application.read.service';
import { PIPELINE_CONFIG_SERVICE, PipelineConfigService } from 'core/pipeline/config/services/pipelineConfig.service';
import { PropertyCommand } from '../domain/propertyCommand.model';
import { IPlatformProperty } from '../domain/platformProperty.model';
import { Application } from 'core/application/application.model';
import { FastPropertyReaderService } from '../fastProperty.read.service';
import { IExecution } from 'core/domain/IExecution';

interface IState {
  submitting: boolean;
}

class DeleteFastPropertyWizardController {

  public command: PropertyCommand = new PropertyCommand();
  public state: IState;

  constructor (
    public $scope: IScope,
    public $uibModalInstance: IModalServiceInstance,
    public title: string,
    public application: Application,
    public property: IPlatformProperty,
    public pipelineConfigService: PipelineConfigService,
    public fastPropertyReader: FastPropertyReaderService,
    public $state: StateService) {
      'ngInject';
      this.state = {
        submitting: false
      };

      this.command.type = PropertyCommandType.DELETE;
      this.command.buildPropertyAndScope(property);
      this.command.applicationName = this.application.name;
  }

  public showSubmitButton() {
    return !!this.command.pipeline;
  }

  public isValid(): boolean {
    return !!this.command.pipeline;
  }

  public cancel(): void {
    this.$uibModalInstance.dismiss();
  }

  public startPipeline(pipeline: PropertyPipeline)  {
    this.state.submitting = true;
    this.pipelineConfigService.startAdHocPipeline(pipeline).then((executionId) => {
      this.fastPropertyReader.waitForPromotionPipelineToAppear(this.application, executionId)
        .then((execution: IExecution) => {
          this.application.getDataSource('propertyPromotions').refresh().then(() => {
            this.$state.go(this.getNextState(), this.getNextParams(execution));
            this.$uibModalInstance.close();
          });
        });
    });
  }

  private getNextParams(execution: IExecution): RawParams {
    const nextParams: RawParams = { propertyId: null };
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

export const DELETE_FAST_PROPERTY_WIZARD_CONTROLLER = 'spinnaker.netflix.fastProperty.deleteWizard.controller';

module(DELETE_FAST_PROPERTY_WIZARD_CONTROLLER, [
  FAST_PROPERTY_DETAILS_COMPONENT,
  FAST_PROPERTY_SCOPE_READ_ONLY_COMPONENT,
  FAST_PROPERTY_STRATEGY_COMPONENT,
  FAST_PROPERTY_REVIEW_COMPONENT,
  APPLICATION_READ_SERVICE,
  PIPELINE_CONFIG_SERVICE,
])
  .controller('deleteFastPropertyWizardController', DeleteFastPropertyWizardController );


import { module, IScope } from 'angular';
import { StateService } from 'angular-ui-router';
import { IModalServiceInstance } from 'angular-ui-bootstrap';

import { FAST_PROPERTY_DETAILS_COMPONENT } from './propertyDetails/propertyDetails.component';
import { FAST_PROPERTY_SCOPE_UPDATABLE_COMPONENT } from './propertyScope/propertyScopeUpdatable.component';
import { FAST_PROPERTY_REVIEW_COMPONENT } from './propertyReview/propertyReview.component';
import { FAST_PROPERTY_STRATEGY_COMPONENT } from './propertyStrategy/propertyStrategy.component';
import { APPLICATION_READ_SERVICE } from 'core/application/service/application.read.service';
import { PIPELINE_CONFIG_SERVICE, PipelineConfigService } from 'core/pipeline/config/services/pipelineConfig.service';
import { PropertyCommandType }from '../domain/propertyCommandType.enum';
import { PropertyPipeline } from '../domain/propertyPipeline.domain';
import { PropertyCommand } from '../domain/propertyCommand.model';
import { IPlatformProperty } from '../domain/platformProperty.model';
import { Application } from 'core/application/application.model';
import { FastPropertyReaderService } from '../fastProperty.read.service';

interface IState {
  submitting: boolean;
}

class UpdateFastPropertyWizardController {

  public state: IState;
  public command: PropertyCommand = new PropertyCommand();

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

      this.command.type = PropertyCommandType.UPDATE;
      this.command.buildPropertyAndScope(property);
      this.command.applicationName = this.application.name;
  }

  public showSubmitButton() {
    return !!this.command.pipeline;
  }

  public startPipeline(pipeline: PropertyPipeline) {
    this.state.submitting = true;
    this.pipelineConfigService.startAdHocPipeline(pipeline).then((executionId) => {
      this.fastPropertyReader.waitForPromotionPipelineToAppear(this.application, executionId)
        .then(() => {
          let nextState = this.$state.current.name.endsWith('.execution') ? '.' : '.execution';
          if (this.application.global) {
            if (this.$state.current.name.includes('.properties')) {
              nextState = '^.rollouts.execution';
            } else {
              nextState = this.$state.current.name.includes('.rollouts.execution') ? '.' : '.execution';
            }
          }
          this.application.getDataSource('propertyPromotions').refresh().then(() => {
            this.$state.go(nextState, { executionId, propertyId: this.command.property.propertyId });
            this.$uibModalInstance.close();
          });
        });
    });
  }

  public isValid() {
    return !!this.command.pipeline;
  }

  public cancel() {
    this.$uibModalInstance.dismiss();
  }

}

export const UPDATE_FAST_PROPERTY_WIZARD_CONTROLLER = 'spinnaker.netflix.fastProperty.updateWizard.controller';

module(UPDATE_FAST_PROPERTY_WIZARD_CONTROLLER, [
  FAST_PROPERTY_DETAILS_COMPONENT,
  FAST_PROPERTY_SCOPE_UPDATABLE_COMPONENT,
  FAST_PROPERTY_STRATEGY_COMPONENT,
  FAST_PROPERTY_REVIEW_COMPONENT,
  APPLICATION_READ_SERVICE,
  PIPELINE_CONFIG_SERVICE,
])
  .controller('updateFastPropertyWizardController', UpdateFastPropertyWizardController );


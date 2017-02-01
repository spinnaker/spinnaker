import {module} from 'angular';

import { FAST_PROPERTY_DETAILS_COMPONENT } from './propertyDetails/propertyDetails.componet';
import { FAST_PROPERTY_SCOPE_COMPONENT } from './propertyScope/propertyScope.component';
import { FAST_PROPERTY_REVIEW_COMPONENT } from './propertyReview/propertyReview.componet';
import { FAST_PROPERTY_STRATEGY_COMPONENT } from './propertyStrategy/propertyStrategy.componet';
import { PIPELINE_CONFIG_SERVICE } from 'core/pipeline/config/services/pipelineConfig.service';
import { PROPERTY_MONITOR_SERVICE } from './monitor/propertyMonitorService';
import { PropertyCommandType }from '../domain/propertyCommandType.enum';
import {PropertyPipeline} from '../domain/propertyPipeline.domain';
import IModalServiceInstance = angular.ui.bootstrap.IModalServiceInstance;
import {PropertyCommand} from '../domain/propertyCommand.model';

class CreateFastPropertyWizardController {

  public command: PropertyCommand = new PropertyCommand();
  public loading = false;
  public propertyMonitor: any;

  static get $inject() { return [
    '$scope',
    '$uibModalInstance',
    'title',
    'applicationName',
    'pipelineConfigService',
    'propertyMonitorService',
  ]; }

  constructor (
    public $scope: ng.IScope,
    public $uibModalInstance: IModalServiceInstance,
    public title: string,
    public applicationName: string,
    public pipelineConfigService: any,
    public propertyMonitorService: any
  ) {
    this.command.type = PropertyCommandType.CREATE;
    this.command.applicationName = this.applicationName;
    this.propertyMonitor = propertyMonitorService.buildMonitor({
      title: 'Creating New Property',
      modalInstance: $uibModalInstance,
      applicationName: this.applicationName ? this.applicationName : 'spinnakerfp'
    });
  }

  public showSubmitButton(): boolean {
    return !!this.command.pipeline;
  }

  public startPipeline(pipeline: PropertyPipeline): void {
    let submit = () => this.pipelineConfigService.startAdHocPipeline(pipeline);
    this.propertyMonitor.submit(submit);
  }


  public isValid(): boolean {
    return !!this.command.pipeline;
  }

  public cancel(): void {
    this.$uibModalInstance.dismiss();
  }

}

export const CREATE_FAST_PROPERTY_WIZARD_CONTROLLER = 'spinnaker.netfilx.fastProperty.createWizard.controller';

module(CREATE_FAST_PROPERTY_WIZARD_CONTROLLER, [
  FAST_PROPERTY_DETAILS_COMPONENT,
  FAST_PROPERTY_SCOPE_COMPONENT,
  FAST_PROPERTY_STRATEGY_COMPONENT,
  FAST_PROPERTY_REVIEW_COMPONENT,
  PIPELINE_CONFIG_SERVICE,
  PROPERTY_MONITOR_SERVICE,
])
  .controller('createFastPropertyWizardController', CreateFastPropertyWizardController);


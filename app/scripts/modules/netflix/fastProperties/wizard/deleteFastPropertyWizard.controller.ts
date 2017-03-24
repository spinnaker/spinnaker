import {module} from 'angular';

import { FAST_PROPERTY_DETAILS_COMPONENT } from './propertyDetails/propertyDetails.component';
import { FAST_PROPERTY_SCOPE_READ_ONLY_COMPONENT } from './propertyScope/propertyScopeReadOnly.component';
import { FAST_PROPERTY_REVIEW_COMPONENT } from './propertyReview/propertyReview.component';
import { FAST_PROPERTY_STRATEGY_COMPONENT } from './propertyStrategy/propertyStrategy.component';
import { PropertyCommandType }from '../domain/propertyCommandType.enum';
import {PropertyPipeline} from '../domain/propertyPipeline.domain';
import IModalServiceInstance = angular.ui.bootstrap.IModalServiceInstance;
import {APPLICATION_READ_SERVICE, ApplicationReader} from 'core/application/service/application.read.service';
import { PROPERTY_MONITOR_SERVICE } from './monitor/propertyMonitorService';
import {PIPELINE_CONFIG_SERVICE, PipelineConfigService} from 'core/pipeline/config/services/pipelineConfig.service';
import {PropertyCommand} from '../domain/propertyCommand.model';
import {IPlatformProperty} from '../domain/platformProperty.model';

class DeleteFastPropertyWizardController {

  public command: PropertyCommand = new PropertyCommand();
  public loading = false;
  public propertyMonitor: any;
  public isDeleting: boolean;

  static get $inject() { return [
    '$scope',
    '$uibModalInstance',
    'title',
    'applicationName',
    'property',
    'pipelineConfigService',
    'propertyMonitorService',
    'applicationReader',
  ]; }

  constructor (
    public $scope: ng.IScope,
    public $uibModalInstance: IModalServiceInstance,
    public title: string,
    public applicationName: string,
    public property: IPlatformProperty,
    public pipelineConfigService: PipelineConfigService,
    public propertyMonitorService: any,
    public applicationReader: ApplicationReader
  ) {

    this.isDeleting = true;

    this.command.type = PropertyCommandType.DELETE;
    this.command.buildPropertyAndScope(property);
    this.command.applicationName = this.applicationName;
    this.propertyMonitor = propertyMonitorService.buildMonitor({
      title: 'Deleting Property',
      modalInstance: $uibModalInstance,
      applicationName: this.applicationName ? this.applicationName : 'spinnakerfp'
    });
  }

  public showSubmitButton() {
    return !!this.command.pipeline;
  }

  public startPipeline(pipeline: PropertyPipeline)  {
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

export const DELETE_FAST_PROPERTY_WIZARD_CONTROLLER = 'spinnaker.netflix.fastProperty.deleteWizard.controller';

module(DELETE_FAST_PROPERTY_WIZARD_CONTROLLER, [
  FAST_PROPERTY_DETAILS_COMPONENT,
  FAST_PROPERTY_SCOPE_READ_ONLY_COMPONENT,
  FAST_PROPERTY_STRATEGY_COMPONENT,
  FAST_PROPERTY_REVIEW_COMPONENT,
  APPLICATION_READ_SERVICE,
  PIPELINE_CONFIG_SERVICE,
  PROPERTY_MONITOR_SERVICE,
])
  .controller('deleteFastPropertyWizardController', DeleteFastPropertyWizardController );


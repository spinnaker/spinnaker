import {module, IScope} from 'angular';

import { FAST_PROPERTY_DETAILS_COMPONENT } from './propertyDetails/propertyDetails.component';
import { FAST_PROPERTY_SCOPE_COMPONENT } from './propertyScope/propertyScope.component';
import { FAST_PROPERTY_REVIEW_COMPONENT } from './propertyReview/propertyReview.component';
import { FAST_PROPERTY_STRATEGY_COMPONENT } from './propertyStrategy/propertyStrategy.component';
import { PIPELINE_CONFIG_SERVICE } from 'core/pipeline/config/services/pipelineConfig.service';
import { PROPERTY_MONITOR_SERVICE } from './monitor/propertyMonitorService';
import { PropertyCommandType }from '../domain/propertyCommandType.enum';
import { PropertyPipeline } from '../domain/propertyPipeline.domain';
import { IModalServiceInstance } from 'angular-ui-bootstrap';
import { PropertyCommand } from '../domain/propertyCommand.model';
import { IPlatformProperty } from '../domain/platformProperty.model';

class CloneFastPropertyToNewScopeWizardController {

  public command: PropertyCommand = new PropertyCommand();
  public loading = false;
  public propertyMonitor: any;
  public isEditing = true;

  static get $inject() { return [
    '$scope',
    '$uibModalInstance',
    'title',
    'property',
    'applicationName',
    'pipelineConfigService',
    'propertyMonitorService',
  ]; }

  constructor (
    public $scope: IScope,
    public $uibModalInstance: IModalServiceInstance,
    public title: string,
    public property: IPlatformProperty,
    public applicationName: string,
    public pipelineConfigService: any,
    public propertyMonitorService: any
  ) {
    this.isEditing = true;
    this.command.type = PropertyCommandType.CREATE;
    this.command.applicationName = this.applicationName;
    this.command.buildPropertyWithoutId(property);
    this.propertyMonitor = propertyMonitorService.buildMonitor({
      title: 'Clone with new scope',
      modalInstance: $uibModalInstance,
      applicationName: this.applicationName ? this.applicationName : 'spinnakerfp'
    });
  }

  public showSubmitButton(): boolean {
    return !!this.command.pipeline;
  }

  public startPipeline(pipeline: PropertyPipeline): void {
    const submit = () => this.pipelineConfigService.startAdHocPipeline(pipeline);
    this.propertyMonitor.submit(submit);
  }

  public isValid(): boolean {
    return !!this.command.pipeline && !!this.command.property.isValid();
  }

  public cancel(): void {
    this.$uibModalInstance.dismiss();
  }

}

export const CLONE_FAST_PROPERTY_TO_NEW_SCOPE_WIZARD_CONTROLLER = 'spinnaker.netflix.fastProperty.clonePropertyToNewScope.controller';

module(CLONE_FAST_PROPERTY_TO_NEW_SCOPE_WIZARD_CONTROLLER, [
  FAST_PROPERTY_DETAILS_COMPONENT,
  FAST_PROPERTY_SCOPE_COMPONENT,
  FAST_PROPERTY_STRATEGY_COMPONENT,
  FAST_PROPERTY_REVIEW_COMPONENT,
  PIPELINE_CONFIG_SERVICE,
  PROPERTY_MONITOR_SERVICE,
])
  .controller('cloneFastPropertyToNewScopeWizardController', CloneFastPropertyToNewScopeWizardController);


import {module, IScope} from 'angular';

import { IModalServiceInstance } from 'angular-ui-bootstrap';
import { FAST_PROPERTY_DETAILS_COMPONENT } from './propertyDetails/propertyDetails.component';
import { FAST_PROPERTY_SCOPE_UPDATABLE_COMPONENT } from './propertyScope/propertyScopeUpdatable.component';
import { FAST_PROPERTY_REVIEW_COMPONENT } from './propertyReview/propertyReview.component';
import { FAST_PROPERTY_STRATEGY_COMPONENT } from './propertyStrategy/propertyStrategy.component';
import { APPLICATION_READ_SERVICE, ApplicationReader } from 'core/application/service/application.read.service';
import { PROPERTY_MONITOR_SERVICE, PropertyMonitorService} from './monitor/propertyMonitorService';
import { PIPELINE_CONFIG_SERVICE, PipelineConfigService } from 'core/pipeline/config/services/pipelineConfig.service';
import { PropertyCommandType }from '../domain/propertyCommandType.enum';
import { PropertyPipeline } from '../domain/propertyPipeline.domain';
import { PropertyCommand } from '../domain/propertyCommand.model';
import { IPlatformProperty } from '../domain/platformProperty.model';

class UpdateFastPropertyWizardController {

  public command: PropertyCommand = new PropertyCommand();
  public loading = false;
  public propertyMonitor: any;
  public isEditing: boolean;

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

  public state: { [stateKey: string]: boolean } = {
    loading: false,
  };

  constructor (
    public $scope: IScope,
    public $uibModalInstance: IModalServiceInstance,
    public title: string,
    public applicationName: string,
    public property: IPlatformProperty,
    public pipelineConfigService: PipelineConfigService,
    public propertyMonitorService: PropertyMonitorService,
    public applicationReader: ApplicationReader
  ) {

    this.isEditing = true;

    this.command.type = PropertyCommandType.UPDATE;
    this.command.buildPropertyAndScope(property);
    this.command.applicationName = this.applicationName;
    this.propertyMonitor = propertyMonitorService.buildMonitor({

      title: 'Updating Property',
      modalInstance: $uibModalInstance,
      applicationName: this.applicationName ? this.applicationName : 'spinnakerfp'
    });
  }

  public showSubmitButton() {
    return !!this.command.pipeline;
  }

  public startPipeline(pipeline: PropertyPipeline) {
    let submit = () => this.pipelineConfigService.startAdHocPipeline(pipeline);
    this.propertyMonitor.submit(submit);
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
  PROPERTY_MONITOR_SERVICE,
])
  .controller('updateFastPropertyWizardController', UpdateFastPropertyWizardController );


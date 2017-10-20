import { module, IController, IComponentOptions, IScope } from 'angular';

import { Application } from 'core/application';
import { IExecution, IExecutionStage } from 'core/domain';

export class StageDetailsController implements IController {
  public application: Application;
  public configSections: string[];
  public execution: IExecution;
  public sourceUrl: string;
  public stage: IExecutionStage;

  constructor(private $scope: IScope) { 'ngInject'; }

  public $onInit(): void {
    // This is pretty dirty but executionDetails has its dirty tentacles
    // all over the place. This makes the conversion of the execution directive
    // to a component safe until we tackle converting all the controllers
    // TODO: Convert all the execution details controllers to ES6 controllers and remove references to $scope
    this.updateScope();
  }

  public $onChanges(): void {
    this.updateScope();
  }

  private updateScope(): void {
    this.$scope.application = this.application;
    this.$scope.execution = this.execution;
    this.$scope.configSections = this.configSections;
    this.$scope.stage = this.stage;
  }
}

export class StageDetailsComponent implements IComponentOptions {
  public bindings: any = {
    application: '<',
    configSections: '<',
    execution: '<',
    sourceUrl: '<',
    stage: '<',
  };
  public controller: any = StageDetailsController;
  public template = '<div ng-include="$ctrl.sourceUrl"></div>';
}

export const STAGE_DETAILS_COMPONENT = 'spinnaker.core.delivery.stageDetails.component';
module(STAGE_DETAILS_COMPONENT, [])
  .component('stageDetails', new StageDetailsComponent());

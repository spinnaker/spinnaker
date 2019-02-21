import { module, IController, IComponentOptions, IScope } from 'angular';

import { Application } from 'core/application';
import { IExecution, IExecutionStage, IStageTypeConfig } from 'core/domain';

export class StepExecutionDetailsController implements IController {
  public application: Application;
  public config: IStageTypeConfig;
  public configSections: string[];
  public execution: IExecution;
  public sourceUrl: string;
  public stage: IExecutionStage;

  public static $inject = ['$scope'];
  constructor(private $scope: IScope) {}

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
    this.$scope.config = this.config;
    this.$scope.configSections = this.configSections;
    this.$scope.execution = this.execution;
    this.$scope.stage = this.stage;
  }
}

export class StepExecutionDetailsComponent implements IComponentOptions {
  public bindings: any = {
    application: '<',
    config: '<',
    configSections: '<',
    execution: '<',
    sourceUrl: '<',
    stage: '<',
  };
  public controller: any = StepExecutionDetailsController;
  public template = '<div ng-include="$ctrl.sourceUrl"></div>';
}

export const STEP_EXECUTION_DETAILS_COMPONENT = 'spinnaker.core.pipeline.stepExecutionDetails.component';
module(STEP_EXECUTION_DETAILS_COMPONENT, []).component('stepExecutionDetails', new StepExecutionDetailsComponent());

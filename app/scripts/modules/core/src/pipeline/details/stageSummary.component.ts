import { module, IController, IComponentOptions, IScope } from 'angular';
import { StateParams, StateService } from '@uirouter/angularjs';
import { HtmlRenderer, Parser } from 'commonmark';

import { Application } from 'core/application';
import { IExecution, IExecutionStage, IExecutionStageSummary, IStage } from 'core/domain';
import { PIPELINE_CONFIG_PROVIDER, PipelineConfigProvider } from 'core/pipeline/config/pipelineConfigProvider';

export class StageSummaryController implements IController {
  public application: Application;
  public execution: IExecution;
  public sourceUrl: string;
  public stage: IExecutionStage;
  public stageSummary: IExecutionStageSummary;

  private parser: Parser = new Parser();
  private renderer: HtmlRenderer = new HtmlRenderer();

  constructor(
    private $scope: IScope,
    private $stateParams: StateParams,
    private $state: StateService,
    private pipelineConfig: PipelineConfigProvider,
  ) {
    'ngInject';
  }

  public $onInit(): void {
    this.updateScope();
  }

  public $onChanges(): void {
    this.updateScope();
  }

  private updateScope(): void {
    // This is pretty dirty but executionDetails has its dirty tentacles
    // all over the place. This makes the conversion of the execution directive
    // to a component safe until we tackle converting all the controllers
    // TODO: Convert all the execution details controllers to ES6 controllers and remove references to $scope
    this.$scope.application = this.application;
    this.$scope.execution = this.execution;
    this.$scope.stage = this.stage;
    this.$scope.stageSummary = this.stageSummary;
  }

  public getStepLabel(stage: IStage): string {
    const stageConfig = this.pipelineConfig.getStageConfig(stage);
    if (stageConfig && stageConfig.executionStepLabelUrl) {
      return stageConfig.executionStepLabelUrl;
    } else {
      return require('../../pipeline/config/stages/core/stepLabel.html');
    }
  }

  public getComments(): string {
    // cast comments field to string in case it is set to a number via SpEL
    const parsed = this.parser.parse(this.stageSummary.comments + '');
    return this.renderer.render(parsed);
  }

  public getCurrentStep() {
    return parseInt(this.$stateParams.step, 10);
  }

  public isStepCurrent(index: number): boolean {
    return index === this.getCurrentStep();
  }

  public isRestartable(stage?: IStage): boolean {
    const stageConfig = this.pipelineConfig.getStageConfig(stage);
    if (!stageConfig || stage.isRestarting === true) {
      return false;
    }

    const allowRestart = this.application.attributes.enableRestartRunningExecutions || false;
    if (this.execution.isRunning && !allowRestart) {
      return false;
    }

    return stageConfig.restartable || false;
  }

  public toggleDetails(index: number): void {
    const newStepDetails = this.getCurrentStep() === index ? null : index;
    if (newStepDetails !== null) {
      const newState = { step: newStepDetails } as any;
      const stage = parseInt(this.$stateParams.stage, 10);
      if (stage) {
        newState.stage = stage;
      }
      const subStage = parseInt(this.$stateParams.subStage, 10);
      if (subStage) {
        newState.subStage = subStage;
      }
      this.$state.go('.', newState);
    }
  }
}

export class StageSummaryComponent implements IComponentOptions {
  public bindings: any = {
    application: '<',
    execution: '<',
    sourceUrl: '<',
    stage: '<',
    stageSummary: '<',
  };
  public controller: any = StageSummaryController;
  public template = '<div className="stage-summary-wrapper" ng-include="$ctrl.sourceUrl"></div>';
}

export const STAGE_SUMMARY_COMPONENT = 'spinnaker.core.pipeline.stageSummary.component';
module(STAGE_SUMMARY_COMPONENT, [PIPELINE_CONFIG_PROVIDER]).component('stageSummary', new StageSummaryComponent());

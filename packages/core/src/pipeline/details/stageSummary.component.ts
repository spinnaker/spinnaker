import { StateParams, StateService } from '@uirouter/angularjs';
import { IComponentOptions, IController, IScope, module } from 'angular';
import { HtmlRenderer, Parser } from 'commonmark';
import { Application } from '../../application';
import { ConfirmationModalService } from '../../confirmationModal';
import { IExecution, IExecutionStage, IExecutionStageSummary, IStage } from '../../domain';
import { Registry } from '../../registry';

import { ExecutionService } from '../service/execution.service';

export class StageSummaryController implements IController {
  public application: Application;
  public execution: IExecution;
  public sourceUrl: string;
  public stage: IExecutionStage;
  public stageSummary: IExecutionStageSummary;

  private parser: Parser = new Parser();
  private renderer: HtmlRenderer = new HtmlRenderer();

  public static $inject = ['$scope', '$stateParams', '$state', 'executionService'];
  constructor(
    private $scope: IScope,
    private $stateParams: StateParams,
    private $state: StateService,
    private executionService: ExecutionService,
  ) {}

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
    const stageConfig = Registry.pipeline.getStageConfig(stage);
    if (stageConfig && stageConfig.executionStepLabelUrl) {
      return stageConfig.executionStepLabelUrl;
    } else {
      return require('../config/stages/common/stepLabel.html');
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
    if (stage.isRunning || stage.isCompleted) {
      return false;
    }

    const stageConfig = Registry.pipeline.getStageConfig(stage);
    if (!stageConfig || stage.isRestarting === true) {
      return false;
    }

    const allowRestart = this.application.attributes.enableRestartRunningExecutions || false;
    if (this.execution.isRunning && !allowRestart) {
      return false;
    }

    return stageConfig.restartable || false;
  }

  public canManuallySkip(): boolean {
    const topLevelStage = this.getTopLevelStage();
    return this.stage.isRunning && topLevelStage && topLevelStage.context.canManuallySkip;
  }

  public getTopLevelStage(): IExecutionStage {
    let parentStageId = this.stage.parentStageId;
    let topLevelStage: IExecutionStage = this.stage;
    while (parentStageId) {
      topLevelStage = this.execution.stages.find((stage) => stage.id === parentStageId);
      parentStageId = topLevelStage.parentStageId;
    }
    return topLevelStage;
  }

  public openManualSkipStageModal(): void {
    const topLevelStage = this.getTopLevelStage();
    ConfirmationModalService.confirm({
      header: 'Really skip this stage?',
      buttonText: 'Skip',
      askForReason: true,
      submitJustWithReason: true,
      body: `<div class="alert alert-warning">
          <b>Warning:</b> Skipping this stage may have unpredictable results.
          <ul>
            <li>Mutating changes initiated by this stage will continue and will need to be cleaned up manually.</li>
            <li>Downstream stages that depend on the outputs of this stage may fail or behave unexpectedly.</li>
          </ul>
        </div>
      `,
      submitMethod: (reason: string) =>
        this.executionService
          .patchExecution(this.execution.id, topLevelStage.id, { manualSkip: true, reason })
          .then(() =>
            this.executionService.waitUntilExecutionMatches(this.execution.id, (execution) => {
              const updatedStage = execution.stages.find((stage) => stage.id === topLevelStage.id);
              return updatedStage && updatedStage.status === 'SKIPPED';
            }),
          )
          .then((updated) => this.executionService.updateExecution(this.application, updated)),
    });
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

export const stageSummaryComponent: IComponentOptions = {
  bindings: {
    application: '<',
    execution: '<',
    sourceUrl: '<',
    stage: '<',
    stageSummary: '<',
  },
  controller: StageSummaryController,
  template: '<div className="stage-summary-wrapper" ng-include="$ctrl.sourceUrl"></div>',
};

export const STAGE_SUMMARY_COMPONENT = 'spinnaker.core.pipeline.stageSummary.component';
module(STAGE_SUMMARY_COMPONENT, []).component('stageSummary', stageSummaryComponent);

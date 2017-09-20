import { IScope, module } from 'angular';
import { StateParams, StateService } from '@uirouter/angularjs';
import { Subscription } from 'rxjs';

import { Application } from 'core/application';
import { IExecution, IExecutionStageSummary, IStage } from 'core/domain';
import { EXECUTION_FILTER_SERVICE, ExecutionFilterService } from 'core/delivery/filter/executionFilter.service';
import { PIPELINE_CONFIG_PROVIDER, PipelineConfigProvider } from 'core/pipeline/config/pipelineConfigProvider';

export interface IExecutionStateParams {
  stage?: number;
  subStage?: number;
  step?: number;
  stageId?: string;
  refId?: string;
}

export class ExecutionDetailsController {
  private summarySourceUrl: string;
  private detailsSourceUrl: string;
  private groupsUpdatedSubscription: Subscription;

  private standalone: boolean;
  public execution: IExecution;
  public application: Application;

  constructor(private $scope: IScope,
              private $stateParams: StateParams,
              private $state: StateService,
              private pipelineConfig: PipelineConfigProvider,
              private executionFilterService: ExecutionFilterService) {
    'ngInject';

    this.$scope.$on('$stateChangeSuccess', () => this.setSourceUrls());
  }

  private getStageParamsFromStageId(stageId: string): IExecutionStateParams {
    const summaries = this.execution.stageSummaries || [];

    let stage, subStage, step;
    summaries.some((summary, index) => {
      let stepIndex = (summary.stages || []).findIndex(s2 => s2.id === stageId);
      if (stepIndex !== -1) {
        step = stepIndex;
        stage = index;
        return true;
      }
      if (summary.type === 'group' && summary.groupStages) {
        summary.groupStages.some((groupStage, subIndex) => {
          stepIndex = (groupStage.stages || []).findIndex((gs) => gs.id === stageId);
          if (stepIndex !== -1) {
            step = stepIndex;
            stage = index;
            subStage = subIndex;
            return true;
          }
          return false;
        });
      }
      return false;
    });

    if (stage) {
      return { stage, subStage, step, stageId: null };
    }
    return null;
  }

  private getStageParamsFromRefId(refId: string): IExecutionStateParams {
    const summaries = this.execution.stageSummaries || [];

    let stage, subStage;

    const stageIndex = summaries.findIndex((summary) => summary.refId === refId);
    if (stageIndex !== -1) {
      return { stage: stageIndex, refId: null };
    }

    summaries.some((summary, index) => {
      if (summary.type === 'group' && summary.groupStages) {
        const subStageIndex = summary.groupStages.findIndex(s2 => s2.refId === refId);
        if (subStageIndex !== -1) {
          stage = index;
          subStage = subStageIndex;
          return true;
        }
      }
      return false;
    });
    if (stage && subStage !== undefined) {
      return { stage, subStage, refId: null };
    }

    return { refId: null };
  }

  private getCurrentStage(): { stage: number, subStage: number } {
    if (this.$stateParams.stageId) {
      const params = this.getStageParamsFromStageId(this.$stateParams.stageId);
      if (params) {
        this.$state.go('.', params, { location: 'replace' });
        return { stage: params.stage, subStage: params.subStage };
      }
    }
    if (this.$stateParams.refId) {
      const params = this.getStageParamsFromRefId(this.$stateParams.refId);
      if (params) {
        this.$state.go('.', params, { location: 'replace' });
        return { stage: params.stage, subStage: params.subStage };
      }
    }

    return { stage: parseInt(this.$stateParams.stage, 10), subStage: parseInt(this.$stateParams.subStage, 10) };
  }

  private getCurrentStep() {
    return parseInt(this.$stateParams.step, 10);
  }

  public close() {
    this.$state.go('^');
  }

  public toggleDetails(index: number): void {
    const newStepDetails = this.getCurrentStep() === index ? null : index;
    if (newStepDetails !== null) {
      const { stage, subStage } = this.getCurrentStage();
      this.$state.go('.', {
        stage,
        subStage,
        step: newStepDetails,
      });
    }
  }

  public isStepCurrent(index: number): boolean {
    return index === this.getCurrentStep();
  }

  public closeDetails(): void {
    this.$state.go('.', { step: null });
  }

  private getStageSummary() {
    const { stage, subStage } = this.getCurrentStage();
    const stages = this.execution.stageSummaries || [];
    let currentStage = null;
    if (stage !== undefined) {
      currentStage = stages[stage];
      if (currentStage && subStage !== undefined && currentStage.groupStages) {
        currentStage = currentStage.groupStages[subStage];
      }
    }
    return currentStage;
  }

  private getDetailsSourceUrl(): string {
    if (this.$stateParams.step !== undefined) {
      const stageSummary = this.getStageSummary();
      if (stageSummary) {
        const step = stageSummary.stages[this.getCurrentStep()] || stageSummary.masterStage;
        const stageConfig = this.pipelineConfig.getStageConfig(step);
        if (stageConfig && stageConfig.executionDetailsUrl) {
          if (stageConfig.executionConfigSections) {
            this.$scope.configSections = stageConfig.executionConfigSections;
          }
          return stageConfig.executionDetailsUrl;
        }
        return require('./defaultExecutionDetails.html');
      }
    }
    return null;
  }

  private getSummarySourceUrl(): string {
    if (this.$stateParams.stage !== undefined) {
      const stageSummary = this.getStageSummary();
      if (stageSummary) {
        const stageConfig = this.pipelineConfig.getStageConfig(stageSummary);
        if (stageConfig && stageConfig.executionSummaryUrl) {
          return stageConfig.executionSummaryUrl;
        }
      }
    }
    return require('../../pipeline/config/stages/core/executionSummary.html');
  }

  public updateStage(stageSummary: IExecutionStageSummary) {
    if (stageSummary) {
      this.$scope.stageSummary = stageSummary;
      this.$scope.stage = stageSummary.stages[this.getCurrentStep()] || stageSummary.masterStage;
    }
  }

  public setSourceUrls() {
    this.summarySourceUrl = this.getSummarySourceUrl();
    this.detailsSourceUrl = this.getDetailsSourceUrl();
    this.updateStage(this.getStageSummary());
  }

  public $onInit() {
    this.setSourceUrls();
    this.standalone = this.standalone || false;

    // This is pretty dirty but executionDetails has its dirty tentacles
    // all over the place. This makes the conversion of the execution directive
    // to a component safe until we tackle converting all the controllers
    // TODO: Convert all the execution details controllers to ES6 controllers and remove references to $scope
    this.$scope.standalone = this.standalone;
    this.$scope.application = this.application;
    this.$scope.execution = this.execution;

    // Since stages and tasks can get updated without the reference to the execution changing, subscribe to the execution updated stream here too
    this.groupsUpdatedSubscription = this.executionFilterService.groupsUpdatedStream.subscribe(() => this.$scope.$evalAsync(() => this.updateStage(this.getStageSummary())));
  }

  public $onChanges(): void {
    this.$scope.standalone = this.standalone;
    this.$scope.application = this.application;
    this.$scope.execution = this.execution;
    this.updateStage(this.getStageSummary());
  }

  public $onDestroy(): void {
    this.groupsUpdatedSubscription.unsubscribe();
  }

  public getStepLabel(stage: IStage): string {
    const stageConfig = this.pipelineConfig.getStageConfig(stage);
    if (stageConfig && stageConfig.executionStepLabelUrl) {
      return stageConfig.executionStepLabelUrl;
    } else {
      return require('../../pipeline/config/stages/core/stepLabel.html');
    }
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
}

export const EXECUTION_DETAILS_CONTROLLER = 'spinnaker.core.delivery.details.executionDetails.controller';
module(EXECUTION_DETAILS_CONTROLLER, [
  require('@uirouter/angularjs').default,
  PIPELINE_CONFIG_PROVIDER,
  EXECUTION_FILTER_SERVICE
])
  .controller('executionDetails', ExecutionDetailsController);

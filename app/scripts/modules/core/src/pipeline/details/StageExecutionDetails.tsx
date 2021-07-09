import React from 'react';
import { Subscription } from 'rxjs';

import { StageSummary } from './StageSummary';
import { StepDetails } from './StepDetails';
import { Application } from '../../application/application.model';
import { IExecution, IExecutionStage, IExecutionStageSummary, IStageTypeConfig } from '../../domain';
import { ExecutionFilterService } from '../filter/executionFilter.service';
import { ReactInjector } from '../../reactShims';
import { Registry } from '../../registry';

import './stageExecutionDetails.less';

export interface IStageExecutionDetailsProps {
  application: Application;
  execution: IExecution;
  standalone?: boolean;
}

export interface IStageExecutionDetailsState {
  detailsStageConfig: IStageTypeConfig;
  stageSummary: IExecutionStageSummary;
  stage: IExecutionStage;
  summaryStageConfig: IStageTypeConfig;
}

export interface IExecutionStateParams {
  stage?: number;
  subStage?: number;
  step?: number;
  stageId?: string;
  refId?: string;
}

export class StageExecutionDetails extends React.Component<IStageExecutionDetailsProps, IStageExecutionDetailsState> {
  public static defaultProps: Partial<IStageExecutionDetailsProps> = {
    standalone: false,
  };

  private groupsUpdatedSubscription: Subscription;
  private locationChangeUnsubscribe: Function;

  constructor(props: IStageExecutionDetailsProps) {
    super(props);
    this.state = this.getUpdatedState();
  }

  private getStageParamsFromStageId(stageId: string, summaries: IExecutionStageSummary[]): IExecutionStateParams {
    let stage, subStage, step;
    summaries.some((summary, index) => {
      let stepIndex = (summary.stages || []).findIndex((s2) => s2.id === stageId);
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

  private getStageParamsFromRefId(refId: string, summaries: IExecutionStageSummary[]): IExecutionStateParams {
    let stage, subStage;

    const stageIndex = summaries.findIndex((summary) => summary.refId === refId);
    if (stageIndex !== -1) {
      return { stage: stageIndex, refId: null };
    }

    summaries.some((summary, index) => {
      if (summary.type === 'group' && summary.groupStages) {
        const subStageIndex = summary.groupStages.findIndex((s2) => s2.refId === refId);
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

  private validateStageExists(
    summaries: IExecutionStageSummary[],
    stage: number,
    subStage: number,
  ): { stage: number; subStage: number } {
    if (isNaN(subStage)) {
      subStage = undefined;
    }
    const foundStage = summaries[stage];
    let foundSubStage;

    if (foundStage) {
      if (foundStage.groupStages) {
        foundSubStage = foundStage.groupStages[subStage];
        subStage = foundSubStage ? subStage : 0;
      } else {
        subStage = undefined;
      }
    } else {
      stage = 0;
    }
    return { stage, subStage };
  }

  private getCurrentStage(summaries: IExecutionStageSummary[]): { stage: number; subStage: number } {
    const { $state, $stateParams } = ReactInjector;
    if ($stateParams.stageId) {
      const params = this.getStageParamsFromStageId($stateParams.stageId, summaries);
      if (params) {
        $state.go('.', params, { location: 'replace' });
        return { stage: params.stage, subStage: params.subStage };
      }
    }
    if ($stateParams.refId) {
      const params = this.getStageParamsFromRefId($stateParams.refId, summaries);
      if (params) {
        $state.go('.', params, { location: 'replace' });
        return { stage: params.stage, subStage: params.subStage };
      }
    }

    const stateStage = parseInt($stateParams.stage, 10);
    const stateSubStage = $stateParams.subStage !== undefined ? parseInt($stateParams.subStage, 10) : undefined;
    const { stage, subStage } = this.validateStageExists(summaries, stateStage, stateSubStage);
    if (stage !== stateStage || subStage !== stateSubStage) {
      $state.go('.', { stage, subStage }, { location: 'replace' });
    }

    return { stage, subStage };
  }

  private getCurrentStep() {
    return parseInt(ReactInjector.$stateParams.step, 10);
  }

  private getStageSummary() {
    const stages = this.props.execution.stageSummaries || [];
    const { stage, subStage } = this.getCurrentStage(stages);
    let currentStage = null;
    if (stage !== undefined) {
      currentStage = stages[stage];
      if (currentStage && subStage !== undefined && currentStage.groupStages) {
        currentStage = currentStage.groupStages[subStage];
      }
    }
    return currentStage;
  }

  private getDetailsStageConfig(stageSummary: IExecutionStageSummary): IStageTypeConfig {
    if (stageSummary && ReactInjector.$stateParams.step !== undefined) {
      const step = stageSummary.stages[this.getCurrentStep()] || stageSummary.masterStage;
      return Registry.pipeline.getStageConfig(step);
    }
    return null;
  }

  private getSummaryStageConfig(stageSummary: IExecutionStageSummary): IStageTypeConfig {
    if (stageSummary && ReactInjector.$stateParams.stage !== undefined) {
      return Registry.pipeline.getStageConfig(stageSummary);
    }
    return {} as IStageTypeConfig;
  }

  public getUpdatedState(): IStageExecutionDetailsState {
    const stageSummary = this.getStageSummary();
    if (stageSummary) {
      const stage = stageSummary.stages[this.getCurrentStep()] || stageSummary.masterStage;
      const summaryStageConfig = this.getSummaryStageConfig(stageSummary);
      const detailsStageConfig = this.getDetailsStageConfig(stageSummary);
      return { stageSummary, stage, summaryStageConfig, detailsStageConfig };
    }
    return {} as IStageExecutionDetailsState;
  }

  public updateStage() {
    this.setState(this.getUpdatedState());
  }

  public componentDidMount(): void {
    this.locationChangeUnsubscribe = ReactInjector.$uiRouter.transitionService.onSuccess({}, () => this.updateStage());
    // Since stages and tasks can get updated without the reference to the execution changing, subscribe to the execution updated stream here too
    this.groupsUpdatedSubscription = ExecutionFilterService.groupsUpdatedStream.subscribe(() => this.updateStage());

    this.updateStage();
  }

  public componentWillReceiveProps() {
    this.updateStage();
  }

  public componentWillUnmount(): void {
    this.groupsUpdatedSubscription.unsubscribe();
    this.locationChangeUnsubscribe();
  }

  public render() {
    const { application, execution } = this.props;
    const { detailsStageConfig, stage, stageSummary, summaryStageConfig } = this.state;

    return (
      <div className="execution-details">
        <StageSummary
          application={application}
          execution={execution}
          config={summaryStageConfig}
          stage={stage}
          stageSummary={stageSummary}
        />
        <StepDetails application={application} execution={execution} stage={stage} config={detailsStageConfig} />
      </div>
    );
  }
}

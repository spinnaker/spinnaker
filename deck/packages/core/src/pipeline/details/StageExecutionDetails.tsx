import React from 'react';
import type { Subscription } from 'rxjs';

import { StageSummary } from './StageSummary';
import { StepDetails } from './StepDetails';
import type { Application } from '../../application/application.model';
import type { IExecution, IExecutionStage, IExecutionStageSummary, IStageTypeConfig } from '../../domain';
import { ExecutionFilterService } from '../filter/executionFilter.service';
import type { IRouterInjectedProps } from '../../navigation/routerContext';
import { withRouter } from '../../navigation/routerContext';
import { SpinErrorBoundary } from '../../presentation';
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
  executionId?: string;
  stage?: number;
  subStage?: number;
  step?: number;
  stageId?: string;
  refId?: string;
}

export class StageExecutionDetailsComponent extends React.Component<
  IStageExecutionDetailsProps & IRouterInjectedProps,
  IStageExecutionDetailsState
> {
  public static defaultProps: Partial<IStageExecutionDetailsProps> = {
    standalone: false,
  };

  private groupsUpdatedSubscription: Subscription;

  constructor(props: IStageExecutionDetailsProps & IRouterInjectedProps) {
    super(props);
    this.state = this.getUpdatedState(props);
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

  private getCurrentStage(
    summaries: IExecutionStageSummary[],
    props: IStageExecutionDetailsProps & IRouterInjectedProps,
  ): { stage: number; subStage: number } {
    const { stateParams, stateService } = props;
    if (stateParams.stageId) {
      const params = this.getStageParamsFromStageId(stateParams.stageId, summaries);
      if (params) {
        stateService.go('.', params, { location: 'replace' });
        return { stage: params.stage, subStage: params.subStage };
      }
    }
    if (stateParams.refId) {
      const params = this.getStageParamsFromRefId(stateParams.refId, summaries);
      if (params) {
        stateService.go('.', params, { location: 'replace' });
        return { stage: params.stage, subStage: params.subStage };
      }
    }

    const stateStage = parseInt(stateParams.stage, 10);
    const stateSubStage = stateParams.subStage !== undefined ? parseInt(stateParams.subStage, 10) : undefined;
    const { stage, subStage } = this.validateStageExists(summaries, stateStage, stateSubStage);
    if (stage !== stateStage || subStage !== stateSubStage) {
      stateService.go('.', { stage, subStage }, { location: 'replace' });
    }

    return { stage, subStage };
  }

  private getCurrentStep(props: IStageExecutionDetailsProps & IRouterInjectedProps) {
    return parseInt(props.stateParams.step, 10);
  }

  private getStageSummary(props: IStageExecutionDetailsProps & IRouterInjectedProps) {
    const stages = props.execution.stageSummaries || [];
    const { stage, subStage } = this.getCurrentStage(stages, props);
    let currentStage = null;
    if (stage !== undefined) {
      currentStage = stages[stage];
      if (currentStage && subStage !== undefined && currentStage.groupStages) {
        currentStage = currentStage.groupStages[subStage];
      }
    }
    return currentStage;
  }

  private getDetailsStageConfig(
    stageSummary: IExecutionStageSummary,
    props: IStageExecutionDetailsProps & IRouterInjectedProps,
  ): IStageTypeConfig {
    if (stageSummary && props.stateParams.step !== undefined) {
      const step = stageSummary.stages[this.getCurrentStep(props)] || stageSummary.masterStage;
      return Registry.pipeline.getStageConfig(step);
    }
    return null;
  }

  private getSummaryStageConfig(
    stageSummary: IExecutionStageSummary,
    props: IStageExecutionDetailsProps & IRouterInjectedProps,
  ): IStageTypeConfig {
    if (stageSummary && props.stateParams.stage !== undefined) {
      return Registry.pipeline.getStageConfig(stageSummary);
    }
    return {} as IStageTypeConfig;
  }

  public getUpdatedState(
    props: IStageExecutionDetailsProps & IRouterInjectedProps = this.props,
  ): IStageExecutionDetailsState {
    if (props.stateParams.executionId && props.stateParams.executionId !== props.execution.id) {
      return {} as IStageExecutionDetailsState;
    }

    const stageSummary = this.getStageSummary(props);
    if (stageSummary) {
      const stage = stageSummary.stages[this.getCurrentStep(props)] || stageSummary.masterStage;
      const summaryStageConfig = this.getSummaryStageConfig(stageSummary, props);
      const detailsStageConfig = this.getDetailsStageConfig(stageSummary, props);
      return { stageSummary, stage, summaryStageConfig, detailsStageConfig };
    }
    return {} as IStageExecutionDetailsState;
  }

  public updateStage(props: IStageExecutionDetailsProps & IRouterInjectedProps = this.props) {
    this.setState(this.getUpdatedState(props));
  }

  public componentDidMount(): void {
    // Since stages and tasks can get updated without the reference to the execution changing, subscribe to the execution updated stream here too
    this.groupsUpdatedSubscription = ExecutionFilterService.groupsUpdatedStream.subscribe(() => this.updateStage());

    this.updateStage();
  }

  public componentWillReceiveProps(nextProps: IStageExecutionDetailsProps & IRouterInjectedProps) {
    this.updateStage(nextProps);
  }

  public componentWillUnmount(): void {
    this.groupsUpdatedSubscription.unsubscribe();
  }

  public render() {
    const { application, execution } = this.props;
    const { detailsStageConfig, stage, stageSummary, summaryStageConfig } = this.state;

    return (
      <SpinErrorBoundary category="StageExecutionDetails">
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
      </SpinErrorBoundary>
    );
  }
}

export const StageExecutionDetails = withRouter<IStageExecutionDetailsProps & IRouterInjectedProps>(
  StageExecutionDetailsComponent,
);

import { UISref } from '@uirouter/react';
import { isEmpty } from 'lodash';
import React from 'react';

import { ExecutionMarkerInformationModal } from './ExecutionMarkerInformationModal';
import { OrchestratedItemRunningTime } from './OrchestratedItemRunningTime';
import { Application } from '../../../application/application.model';
import { SETTINGS } from '../../../config/settings';
import { ExecutionBarLabel } from '../../config/stages/common/ExecutionBarLabel';
import { IExecution, IExecutionStageSummary } from '../../../domain';
import { logger } from '../../../utils';
import { duration } from '../../../utils/timeFormatters';

import './executionMarker.less';

export interface IExecutionMarkerProps {
  active?: boolean;
  application: Application;
  execution: IExecution;
  onClick: (stageIndex: number) => void;
  previousStageActive?: boolean;
  stage: IExecutionStageSummary;
  width: string;
}

export interface IExecutionMarkerState {
  duration: string;
  hydrated: boolean;
  showingExecutionMarkerInformationModal: boolean;
}

export class ExecutionMarker extends React.Component<IExecutionMarkerProps, IExecutionMarkerState> {
  private runningTime: OrchestratedItemRunningTime;

  constructor(props: IExecutionMarkerProps) {
    super(props);

    const { stage, execution } = props;

    this.state = {
      duration: duration(stage.runningTimeInMs),
      hydrated: execution.hydrated,
      showingExecutionMarkerInformationModal: false,
    };
  }

  public componentDidMount() {
    this.runningTime = new OrchestratedItemRunningTime(this.props.stage, (time: number) =>
      this.setState({ duration: duration(time) }),
    );
  }

  public componentWillReceiveProps(nextProps: IExecutionMarkerProps) {
    this.runningTime.checkStatus(nextProps.stage);
  }

  public componentWillUnmount() {
    this.runningTime.reset();
  }

  private handleStageClick = (): void => {
    logger.log({ category: 'Pipeline', action: 'Stage clicked (bar)' });
    this.props.onClick(this.props.stage.index);
  };

  private handleStageInformationClick = (event: any): void => {
    logger.log({ category: 'Pipeline', action: 'Stage show context menu (bar)' });
    event.preventDefault();
    event.stopPropagation();
    this.showExecutionMarkerInformationModal();
  };

  private showExecutionMarkerInformationModal = () => {
    this.setState({
      showingExecutionMarkerInformationModal: true,
    });
  };

  private hideExecutionMarkerInformationModal = () => {
    this.setState({
      showingExecutionMarkerInformationModal: false,
    });
  };

  private stageStatus = (stageStatus: string) => {
    if (stageStatus === 'running') {
      const currentStatus = this.props.stage.stages.filter(
        (stage) => stage.status.toLowerCase() === 'running' && stage.type === 'pipeline' && !isEmpty(stage.others),
      );
      if (!isEmpty(currentStatus)) return 'waiting';
    }
    return stageStatus;
  };

  public render() {
    const { stage, application, execution, active, previousStageActive, width } = this.props;
    const stageType = (stage.activeStageType || stage.type).toLowerCase(); // support groups
    const pipelineStatus = this.stageStatus(stage.status.toLowerCase());
    const markerClassName = [
      stage.type !== 'group' ? 'clickable' : '',
      'stage',
      'execution-marker',
      `stage-type-${stageType}`,
      `execution-marker-${pipelineStatus}`,
      active ? 'active' : '',
      previousStageActive ? 'after-active' : '',
      stage.isRunning ? 'glowing' : '',
      stage.requiresAttention ? 'requires-attention' : '',
    ].join(' ');

    const TooltipComponent = stage.useCustomTooltip ? stage.labelComponent : ExecutionBarLabel;
    const MarkerIcon = stage.markerIcon;
    const showInfoIcon =
      SETTINGS.feature.executionMarkerInformationModal &&
      stage.status.toLowerCase() === 'terminal' &&
      stage.type === 'pipeline';
    const stageContents =
      pipelineStatus === 'waiting' ? (
        <div className={markerClassName} style={{ width, backgroundColor: stage.color }}>
          <UISref
            to="home.applications.application.pipelines.executionDetails.execution"
            params={{
              application: stage.stages[0].others.leafnodeApplicationName,
              executionId: stage.stages[0].others.leafnodePipelineExecutionId,
              executionParams: { application: application.name, executionId: execution.id },
            }}
          >
            <a target="_self" className="execution-waiting-link">
              <span className="horizontal center middle">
                <span className="duration">waiting </span>
                {<i className="fa fa-clock execution-waiting-fa"></i>}
              </span>
            </a>
          </UISref>
        </div>
      ) : (
        <div
          className={markerClassName}
          style={{ width, backgroundColor: stage.color }}
          onClick={this.handleStageClick}
        >
          <span className="horizontal center middle">
            <MarkerIcon stage={stage} />
            <span className="duration">{this.state.duration}</span>
            {showInfoIcon && <i className="fa fa-info-circle" onClick={this.handleStageInformationClick} />}
          </span>
        </div>
      );
    return (
      <span>
        <TooltipComponent application={application} execution={execution} stage={stage} executionMarker={true}>
          {stageContents}
        </TooltipComponent>
        {this.state.showingExecutionMarkerInformationModal && (
          <ExecutionMarkerInformationModal
            executionId={execution.id}
            onClose={this.hideExecutionMarkerInformationModal}
            stageId={execution.stageSummaries[stage.index].id}
          />
        )}
      </span>
    );
  }
}

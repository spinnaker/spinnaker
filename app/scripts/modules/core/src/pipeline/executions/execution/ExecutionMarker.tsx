import React from 'react';
import ReactGA from 'react-ga';

import { IExecution, IExecutionStageSummary } from 'core/domain';
import { OrchestratedItemRunningTime } from './OrchestratedItemRunningTime';
import { duration } from 'core/utils/timeFormatters';

import { Application } from 'core/application/application.model';
import { ExecutionBarLabel } from '../../config/stages/common/ExecutionBarLabel';

import './executionMarker.less';

export interface IExecutionMarkerProps {
  stage: IExecutionStageSummary;
  application: Application;
  execution: IExecution;
  active?: boolean;
  previousStageActive?: boolean;
  width: string;
  onClick: (stageIndex: number) => void;
}

export interface IExecutionMarkerState {
  duration: string;
  hydrated: boolean;
}

export class ExecutionMarker extends React.Component<IExecutionMarkerProps, IExecutionMarkerState> {
  private runningTime: OrchestratedItemRunningTime;

  constructor(props: IExecutionMarkerProps) {
    super(props);

    const { stage, execution } = props;

    this.state = {
      duration: duration(stage.runningTimeInMs),
      hydrated: execution.hydrated,
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
    ReactGA.event({ category: 'Pipeline', action: 'Stage clicked (bar)' });
    this.props.onClick(this.props.stage.index);
  };

  public render() {
    const { stage, application, execution, active, previousStageActive, width } = this.props;
    const stageType = (stage.activeStageType || stage.type).toLowerCase(); // support groups
    const markerClassName = [
      stage.type !== 'group' ? 'clickable' : '',
      'stage',
      'execution-marker',
      `stage-type-${stageType}`,
      `execution-marker-${stage.status.toLowerCase()}`,
      active ? 'active' : '',
      previousStageActive ? 'after-active' : '',
      stage.isRunning ? 'glowing' : '',
      stage.requiresAttention ? 'requires-attention' : '',
    ].join(' ');

    const TooltipComponent = stage.useCustomTooltip ? stage.labelComponent : ExecutionBarLabel;
    const MarkerIcon = stage.markerIcon;
    const stageContents = (
      <div className={markerClassName} style={{ width, backgroundColor: stage.color }} onClick={this.handleStageClick}>
        <span className="horizontal center middle">
          <MarkerIcon stage={stage} />
          <span className="duration">{this.state.duration}</span>
        </span>
      </div>
    );
    return (
      <TooltipComponent application={application} execution={execution} stage={stage} executionMarker={true}>
        {stageContents}
      </TooltipComponent>
    );
  }
}

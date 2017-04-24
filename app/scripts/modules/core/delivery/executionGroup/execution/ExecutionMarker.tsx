import * as React from 'react';
import * as ReactGA from 'react-ga';
import autoBindMethods from 'class-autobind-decorator';

import { IExecution, IExecutionStageSummary } from 'core/domain';
import { OrchestratedItemRunningTime } from './OrchestratedItemRunningTime';
import { duration } from 'core/utils/timeFormatters';

import { Application } from 'core/application/application.model';
import { ExecutionBarLabel } from 'core/pipeline/config/stages/core/ExecutionBarLabel';

import './executionMarker.less';

interface IExecutionMarkerProps {
  stage: IExecutionStageSummary;
  application: Application;
  execution: IExecution;
  active?: boolean;
  previousStageActive?: boolean;
  width: string;
  onClick: (stageIndex: number) => void;
}

interface IExecutionMarkerState {
  duration: string;
}

@autoBindMethods
export class ExecutionMarker extends React.Component<IExecutionMarkerProps, IExecutionMarkerState> {
  private runningTime: OrchestratedItemRunningTime;

  constructor(props: IExecutionMarkerProps) {
    super(props);

    this.state = {
      duration: duration(props.stage.runningTimeInMs)
    };
  }

  public componentDidMount() {
    this.runningTime = new OrchestratedItemRunningTime(this.props.stage, (time: number) => this.setState({ duration: duration(time) }));
  }

  public componentWillReceiveProps() {
    this.runningTime.checkStatus();
  }

  public componentWillUnmount() {
    this.runningTime.reset();
  }

  private handleStageClick(): void {
    ReactGA.event({category: 'Pipeline', action: 'Stage clicked (bar)'});
    this.props.onClick(this.props.stage.index);
  }

  public render() {
    const {stage, application, execution, active, previousStageActive, width} = this.props;
    const markerClassName = [
      'clickable',
      'stage',
      'execution-marker',
      `stage-type-${stage.type.toLowerCase()}`,
      `execution-marker-${stage.status.toLowerCase()}`,
      active ? 'active' : '',
      previousStageActive ? 'after-active' : '',
      stage.isRunning ? 'glowing' : ''
      ].join(' ');

    const TooltipComponent = stage.labelComponent;
    const MarkerIcon = stage.markerIcon;
    const stageContents = (
      <div className={markerClassName}
           style={{width: width, backgroundColor: stage.color}}
           onClick={this.handleStageClick}>
        <MarkerIcon stage={stage}/>
        <span className="duration">{this.state.duration}</span>
      </div>);
    if (stage.useCustomTooltip) {
      return (
        <TooltipComponent application={application} execution={execution} stage={stage} executionMarker={true}>
          {stageContents}
        </TooltipComponent>
      );
    }
    return (
      <ExecutionBarLabel application={application} execution={execution} stage={stage} executionMarker={true}>
        {stageContents}
      </ExecutionBarLabel>);
  }
}

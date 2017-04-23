import * as React from 'react';
import * as ReactGA from 'react-ga';
import autoBindMethods from 'class-autobind-decorator';

import { IExecutionStageSummary } from 'core/domain/IExecutionStage';
import { OrchestratedItemRunningTime } from './OrchestratedItemRunningTime';
import { Tooltip } from 'core/presentation/Tooltip';
import { duration } from 'core/utils/timeFormatters';

import './executionMarker.less';

interface IExecutionMarkerProps {
  stage: IExecutionStageSummary;
  active?: boolean;
  width: string;
  onClick: (stageIndex: number) => void;
}

interface IExecutionMarkerState {
  runningTimeInMs: number;
}

@autoBindMethods
export class ExecutionMarker extends React.Component<IExecutionMarkerProps, IExecutionMarkerState> {
  private runningTime: OrchestratedItemRunningTime;

  constructor(props: IExecutionMarkerProps) {
    super(props);

    this.state = {
      runningTimeInMs: props.stage.runningTimeInMs
    };
  }

  public componentDidMount() {
    this.runningTime = new OrchestratedItemRunningTime(this.props.stage, (time: number) => this.setState({ runningTimeInMs: time }));
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
    const stage = this.props.stage;
    const markerClassName = [
      'clickable',
      'stage',
      'execution-marker',
      `stage-type-${stage.type.toLowerCase()}`,
      `execution-marker-${stage.status.toLowerCase()}`,
      this.props.active ? 'active' : '',
      stage.isRunning ? 'glowing' : ''
      ].join(' ');

    const TooltipTemplate = stage.labelTemplate;
    return (
      <Tooltip key={stage.refId} template={(<TooltipTemplate stage={stage}/>)}>
        <div className={markerClassName}
          style={{width: this.props.width, backgroundColor: stage.color}}
          onClick={this.handleStageClick}>
          <span className="duration">{duration(stage.runningTimeInMs)}</span>
        </div>
      </Tooltip>);
  }
}

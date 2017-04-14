import * as React from 'react';
import * as ReactGA from 'react-ga';

import { IExecutionStage } from 'core/domain/IExecutionStage';
import { OrchestratedItemRunningTime } from './OrchestratedItemRunningTime';
import { Tooltip } from 'core/presentation/Tooltip';
import { duration } from 'core/utils/timeFormatters';

import './executionMarker.less';

interface IExecutionMarkerProps {
  stage: IExecutionStage;
  active: boolean;
  width: string;
  onClick: () => void;
}

interface IExecutionMarkerState {
  runningTimeInMs: number;
}

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
      <Tooltip key={stage.refId} template={(<TooltipTemplate stage={stage}></TooltipTemplate>)}>
        <div className={markerClassName}
          style={{width: this.props.width, backgroundColor: stage.color}}
          onClick={() => {
            ReactGA.event({category: 'Pipeline', action: 'Stage clicked (bar)'});
            this.props.onClick();
          }}>
          <span className="duration">{duration(stage.runningTimeInMs)}</span>
        </div>
      </Tooltip>);
  }
}

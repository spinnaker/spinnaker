import * as React from 'react';
import * as ReactGA from 'react-ga';
import { OverlayTrigger, Tooltip } from 'react-bootstrap';

import { IExecution, IExecutionStageSummary } from 'core/domain';
import { ReactInjector } from 'core/reactShims';
import { Spinner } from 'core/widgets';
import { OrchestratedItemRunningTime } from './OrchestratedItemRunningTime';
import { duration } from 'core/utils/timeFormatters';

import { Application } from 'core/application/application.model';
import { ExecutionBarLabel } from 'core/pipeline/config/stages/common/ExecutionBarLabel';

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
  private mounted = false;

  constructor(props: IExecutionMarkerProps) {
    super(props);

    const { stage, execution } = props;

    this.state = {
      duration: duration(stage.runningTimeInMs),
      hydrated: execution.hydrated,
    };
  }

  private hydrate = (): void => {
    const { execution, application } = this.props;
    ReactInjector.executionService.hydrate(application, execution).then(() => {
      if (this.mounted && !this.state.hydrated) {
        this.setState({ hydrated: true });
      }
    });
  };

  public componentDidMount() {
    this.mounted = true;
    this.runningTime = new OrchestratedItemRunningTime(this.props.stage, (time: number) =>
      this.setState({ duration: duration(time) }),
    );
  }

  public componentWillReceiveProps(nextProps: IExecutionMarkerProps) {
    this.runningTime.checkStatus(nextProps.stage);
  }

  public componentWillUnmount() {
    this.mounted = false;
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
    ].join(' ');

    const TooltipComponent = stage.labelComponent;
    const MarkerIcon = stage.markerIcon;
    const stageContents = (
      <div className={markerClassName} style={{ width, backgroundColor: stage.color }} onClick={this.handleStageClick}>
        <span className="horizontal center middle">
          <MarkerIcon stage={stage} />
          <span className="duration">{this.state.duration}</span>
        </span>
      </div>
    );
    if (stage.useCustomTooltip) {
      if (execution.hydrated) {
        return (
          <TooltipComponent application={application} execution={execution} stage={stage} executionMarker={true}>
            {stageContents}
          </TooltipComponent>
        );
      } else {
        const loadingTooltip = (
          <Tooltip id={stage.id}>
            <Spinner size="small" />
          </Tooltip>
        );
        return (
          <span onMouseEnter={this.hydrate}>
            <OverlayTrigger placement="top" overlay={loadingTooltip}>
              {stageContents}
            </OverlayTrigger>
          </span>
        );
      }
    }
    return (
      <ExecutionBarLabel application={application} execution={execution} stage={stage} executionMarker={true}>
        {stageContents}
      </ExecutionBarLabel>
    );
  }
}

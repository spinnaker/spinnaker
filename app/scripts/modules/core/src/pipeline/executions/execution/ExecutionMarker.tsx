import React from 'react';
import ReactGA from 'react-ga';

import { Application } from 'core/application/application.model';
import { SETTINGS } from 'core/config/settings';
import { IExecution, IExecutionStageSummary } from 'core/domain';
import { duration } from 'core/utils/timeFormatters';

import { ExecutionMarkerInformationModal } from './ExecutionMarkerInformationModal';
import { OrchestratedItemRunningTime } from './OrchestratedItemRunningTime';
import { ExecutionBarLabel } from '../../config/stages/common/ExecutionBarLabel';

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
    ReactGA.event({ category: 'Pipeline', action: 'Stage clicked (bar)' });
    this.props.onClick(this.props.stage.index);
  };

  private handleStageInformationClick = (event: any): void => {
    ReactGA.event({ category: 'Pipeline', action: 'Stage show context menu (bar)' });
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
    const showInfoIcon =
      SETTINGS.feature.executionMarkerInformationModal &&
      stage.status.toLowerCase() === 'terminal' &&
      stage.type === 'pipeline';
    const stageContents = (
      <div className={markerClassName} style={{ width, backgroundColor: stage.color }} onClick={this.handleStageClick}>
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

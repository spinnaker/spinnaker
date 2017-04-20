import * as React from 'react';
import { has } from 'lodash';

import { ExecutionBuildNumber } from 'core/delivery/executionBuild/ExecutionBuildNumber';
import { ExecutionMarker } from 'core/delivery/executionGroup/execution/ExecutionMarker';
import { IExecution } from 'core/domain/index';
import { $state } from 'core/uirouter';
import { timestamp } from 'core/utils/timeFormatters';

import './projectPipeline.less';

interface IProjectPipelineProps {
  execution: IExecution;
}

interface IProjectPipelineState {
  hasBuildInfo: boolean;
  loaded: boolean;
  stageWidth: string;
}

export class ProjectPipeline extends React.Component<IProjectPipelineProps, IProjectPipelineState> {
  constructor(props: IProjectPipelineProps) {
    super(props);
    this.state = {
      hasBuildInfo: this.props.execution.buildInfo || has(this.props.execution, 'trigger.buildInfo') || has(this.props.execution, 'trigger.parentPipelineId'),
      loaded: true,
      stageWidth: `${100 / this.props.execution.stageSummaries.length}%`
    };
  }

  private handleExecutionTitleClick = () => $state.go('^.application.pipelines.executions.execution', {application: this.props.execution.application, executionId: this.props.execution.id});

  private handleStageClick = (stageIndex: number) => $state.go('^.application.pipelines.executionDetails.execution', {application: this.props.execution.application, executionId: this.props.execution.id, stage: stageIndex})

  public render() {
    const stages = this.props.execution.stageSummaries.map((stage) => <ExecutionMarker key={stage.refId} stage={stage} onClick={this.handleStageClick} width={this.state.stageWidth}/>);

    return (
      <div>
        <h5 className="execution-title">
          <a onClick={this.handleExecutionTitleClick}>
            {`${this.props.execution.application.toUpperCase()}: ${this.props.execution.name}`}
          </a>
        </h5>
        &nbsp;(<ExecutionBuildNumber execution={this.props.execution}/>{ this.state.hasBuildInfo && (<span>, </span>) }started {timestamp(this.props.execution.startTime)})
        <div className="execution-bar">
          {stages}
        </div>
      </div>
    );
  }
}

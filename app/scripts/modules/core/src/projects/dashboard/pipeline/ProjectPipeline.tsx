import { has } from 'lodash';
import React from 'react';

import { Application } from '../../../application/application.model';
import { IExecution } from '../../../domain';
import { ExecutionBuildLink } from '../../../pipeline/executionBuild/ExecutionBuildLink';
import { ExecutionMarker } from '../../../pipeline/executions/execution/ExecutionMarker';
import { ReactInjector } from '../../../reactShims';
import { timestamp } from '../../../utils/timeFormatters';

import './projectPipeline.less';

export interface IProjectPipelineProps {
  application: Application;
  execution: IExecution;
}

export interface IProjectPipelineState {
  hasBuildInfo: boolean;
  loaded: boolean;
  stageWidth: string;
}

export class ProjectPipeline extends React.Component<IProjectPipelineProps, IProjectPipelineState> {
  constructor(props: IProjectPipelineProps) {
    super(props);
    this.state = {
      hasBuildInfo:
        this.props.execution.buildInfo ||
        has(this.props.execution, 'trigger.buildInfo') ||
        has(this.props.execution, 'trigger.parentPipelineId'),
      loaded: true,
      stageWidth: `${100 / this.props.execution.stageSummaries.length}%`,
    };
  }

  private handleExecutionTitleClick = (): void => {
    ReactInjector.$state.go('^.application.pipelines.executions.execution', {
      application: this.props.execution.application,
      executionId: this.props.execution.id,
    });
  };

  private handleStageClick = (stageIndex: number) => {
    ReactInjector.$state.go('^.application.pipelines.executionDetails.execution', {
      application: this.props.execution.application,
      executionId: this.props.execution.id,
      stage: stageIndex,
    });
  };

  public render() {
    const execution = this.props.execution;
    const stages = execution.stageSummaries.map((stage) => (
      <ExecutionMarker
        key={stage.refId}
        {...this.props}
        stage={stage}
        onClick={this.handleStageClick}
        width={this.state.stageWidth}
      />
    ));

    return (
      <div>
        <h5 className="execution-title">
          <a onClick={this.handleExecutionTitleClick}>{`${execution.application.toUpperCase()}: ${execution.name}`}</a>
        </h5>
        &nbsp;(
        <ExecutionBuildLink execution={execution} />
        {this.state.hasBuildInfo && <span>, </span>}
        started {timestamp(this.props.execution.startTime)})<div className="execution-bar">{stages}</div>
      </div>
    );
  }
}

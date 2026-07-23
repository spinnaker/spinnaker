import { has } from 'lodash';
import React from 'react';

import type { Application } from '../../../application/application.model';
import type { IExecution } from '../../../domain';
import type { IRouterInjectedProps } from '../../../navigation/routerContext';
import { withRouter } from '../../../navigation/routerContext';
import { ExecutionBuildLink } from '../../../pipeline/executionBuild/ExecutionBuildLink';
import { ExecutionMarker } from '../../../pipeline/executions/execution/ExecutionMarker';
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

class ProjectPipelineComponent extends React.Component<
  IProjectPipelineProps & IRouterInjectedProps,
  IProjectPipelineState
> {
  constructor(props: IProjectPipelineProps & IRouterInjectedProps) {
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
    this.props.stateService.go('^.application.pipelines.executions.execution', {
      application: this.props.execution.application,
      executionId: this.props.execution.id,
    });
  };

  private handleStageClick = (stageIndex: number) => {
    this.props.stateService.go('^.application.pipelines.executionDetails.execution', {
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

export const ProjectPipeline = withRouter(ProjectPipelineComponent);
ProjectPipeline.displayName = 'ProjectPipeline';

import * as React from 'react';
import { has } from 'lodash';

import { ExecutionBuildNumber } from 'core/delivery/executionBuild/ExecutionBuildNumber';
import { IExecution } from 'core/domain/index';
import { Tooltip } from 'core/presentation/Tooltip';
import { stateService } from 'core/state.service';
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

  public render() {
    const stages = this.props.execution.stageSummaries.map((stage, index) => {
      const TooltipTemplate = stage.labelTemplate;
      return (
        <Tooltip key={stage.refId} template={<TooltipTemplate stage={stage}></TooltipTemplate>}>
          <div className={`clickable stage stage-type-${stage.type.toLowerCase()} execution-marker execution-marker-${stage.status.toLowerCase()}`}
              style={{width: this.state.stageWidth, backgroundColor: stage.color}}
              onClick={() => {
                stateService.go('^.application.pipelines.executionDetails.execution', {application: this.props.execution.application, executionId: this.props.execution.id, stage: index});
              }}>
          </div>
        </Tooltip>
      );
    });

    return (
      <div>
        <h5 className="execution-title">
          <a onClick={() => stateService.go('^.application.pipelines.executions.execution', {application: this.props.execution.application, executionId: this.props.execution.id})}>
            {`${this.props.execution.application.toUpperCase()}: ${this.props.execution.name}`}
          </a>
        </h5>
        &nbsp;(<ExecutionBuildNumber execution={this.props.execution}></ExecutionBuildNumber>{ this.state.hasBuildInfo && (<span>, </span>) }started {timestamp(this.props.execution.startTime)})
        <div className="execution-bar">
          {stages}
        </div>
      </div>
    );
  }
}

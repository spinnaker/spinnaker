import * as React from 'react';
import * as ReactGA from 'react-ga';

import { IExecution } from 'core/domain/index';
import { stateService } from 'core/state.service';

import './ExecutionBuildNumber.less';

interface IExecutionBuildNumberProps {
  execution: IExecution;
}

export class ExecutionBuildNumber extends React.Component<IExecutionBuildNumberProps, void> {
  constructor(props: IExecutionBuildNumberProps) {
    super(props);
  }

  public render() {
    return (
      <span>
        { this.props.execution.trigger.parentPipelineId && (
          <a className="execution-build-number clickable"
             onClick={() => {
              ReactGA.event({category: 'Pipeline', action: 'Execution build number clicked - parent pipeline'});
              const toStateParams = {application: this.props.execution.trigger.parentPipelineApplication, executionId: this.props.execution.trigger.parentPipelineId};
              const toStateOptions = {inherit: false, reload: 'home.applications.application.pipelines.executionDetails'};
              stateService.go('^.executionDetails.execution', toStateParams, toStateOptions);
          }}>
            {this.props.execution.trigger.parentPipelineName}
          </a>
        )}
        { this.props.execution.buildInfo && this.props.execution.buildInfo.number && (
          <a className="execution-build-number clickable"
             onClick={(event) => {
              ReactGA.event({category: 'Pipeline', action: 'Execution build number clicked - build info'});
              event.stopPropagation();
            }}
             href={this.props.execution.buildInfo.url}
             target="_blank">
            <span className="build-label">Build</span> #{this.props.execution.buildInfo.number}
          </a>
        )}
      </span>
    );
  }
}

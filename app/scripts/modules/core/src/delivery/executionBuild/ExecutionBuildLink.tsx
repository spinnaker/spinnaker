import * as React from 'react';
import * as ReactGA from 'react-ga';
import { BindAll } from 'lodash-decorators';

import { IExecution } from 'core/domain';
import { ReactInjector } from 'core/reactShims';
import { ExecutionBuildTitle } from './ExecutionBuildTitle';

import './ExecutionBuildLink.less';

export interface IExecutionBuildLinkProps {
  execution: IExecution;
}

@BindAll()
export class ExecutionBuildLink extends React.Component<IExecutionBuildLinkProps, {}> {
  constructor(props: IExecutionBuildLinkProps) {
    super(props);
  }

  private handleParentPipelineClick() {
    const { $state } = ReactInjector;
    ReactGA.event({ category: 'Pipeline', action: 'Execution build number clicked - parent pipeline' });
    const toStateParams = { application: this.props.execution.trigger.parentPipelineApplication, executionId: this.props.execution.trigger.parentPipelineId };
    const toStateOptions = { inherit: false, reload: 'home.applications.application.pipelines.executionDetails' };
    const nextState = `${$state.current.name.endsWith('.execution') ? '^' : ''}.^.executionDetails.execution`;
    $state.go(nextState, toStateParams, toStateOptions);
  }

  private handleBuildInfoClick(event: React.MouseEvent<HTMLElement>) {
    ReactGA.event({ category: 'Pipeline', action: 'Execution build number clicked - build info' });
    event.stopPropagation();
  }

  public render() {
    return (
      <span>
        { this.props.execution.trigger.parentPipelineId && (
          <a
            className="execution-build-number clickable"
            onClick={this.handleParentPipelineClick}
          >
            <ExecutionBuildTitle execution={this.props.execution}/>
          </a>
        )}
        { this.props.execution.buildInfo && this.props.execution.buildInfo.number && (
          <a
            className="execution-build-number clickable"
            onClick={this.handleBuildInfoClick}
            href={this.props.execution.buildInfo.url}
            target="_blank"
          >
            <ExecutionBuildTitle execution={this.props.execution}/>
          </a>
        )}
      </span>
    );
  }
}

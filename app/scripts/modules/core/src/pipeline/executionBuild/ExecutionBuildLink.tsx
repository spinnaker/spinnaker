import * as React from 'react';
import * as ReactGA from 'react-ga';
import { BindAll } from 'lodash-decorators';

import { IExecution } from 'core/domain';
import { ReactInjector } from 'core/reactShims';

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
    const { parentExecution } = this.props.execution.trigger;
    const { $state } = ReactInjector;
    ReactGA.event({ category: 'Pipeline', action: 'Execution build number clicked - parent pipeline' });
    const toStateParams = { application: parentExecution.application, executionId: parentExecution.id };
    const toStateOptions = { inherit: false, reload: 'home.applications.application.pipelines.executionDetails' };
    const nextState = `${$state.current.name.endsWith('.execution') ? '^' : ''}.^.executionDetails.execution`;
    $state.go(nextState, toStateParams, toStateOptions);
  }

  private handleBuildInfoClick(event: React.MouseEvent<HTMLElement>) {
    ReactGA.event({ category: 'Pipeline', action: 'Execution build number clicked - build info' });
    event.stopPropagation();
  }

  public render() {
    const { trigger } = this.props.execution;
    return (
      <span>
        { trigger.parentExecution && trigger.parentExecution.id && (
          <a
            className="execution-build-number clickable"
            onClick={this.handleParentPipelineClick}
          >
            {trigger.parentExecution.name}
          </a>
        )}
        { this.props.execution.buildInfo && this.props.execution.buildInfo.number && (
          <a
            className="execution-build-number clickable"
            onClick={this.handleBuildInfoClick}
            href={this.props.execution.buildInfo.url}
            target="_blank"
          >
            <span className="build-label">Build</span> #{this.props.execution.buildInfo.number}
          </a>
        )}
      </span>
    );
  }
}

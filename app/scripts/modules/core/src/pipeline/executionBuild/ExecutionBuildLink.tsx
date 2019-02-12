import * as React from 'react';
import * as ReactGA from 'react-ga';

import { IExecution } from 'core/domain';
import { ReactInjector } from 'core/reactShims';

import './ExecutionBuildLink.less';

export interface IExecutionBuildLinkProps {
  execution: IExecution;
}

export class ExecutionBuildLink extends React.Component<IExecutionBuildLinkProps, {}> {
  constructor(props: IExecutionBuildLinkProps) {
    super(props);
  }

  private handleParentPipelineClick = () => {
    const { parentExecution } = this.props.execution.trigger;
    const { $state } = ReactInjector;
    ReactGA.event({ category: 'Pipeline', action: 'Execution build number clicked - parent pipeline' });
    const toStateParams = { application: parentExecution.application, executionId: parentExecution.id };
    const toStateOptions = { inherit: false, reload: 'home.applications.application.pipelines.executionDetails' };
    const nextState = `${$state.current.name.endsWith('.execution') ? '^' : ''}.^.executionDetails.execution`;
    $state.go(nextState, toStateParams, toStateOptions);
  };

  private handleBuildInfoClick = (event: React.MouseEvent<HTMLElement>) => {
    ReactGA.event({ category: 'Pipeline', action: 'Execution build number clicked - build info' });
    event.stopPropagation();
  };

  public render() {
    const { trigger } = this.props.execution;
    return (
      <span>
        {trigger.parentExecution && trigger.parentExecution.id && (
          <a className="execution-build-number clickable" onClick={this.handleParentPipelineClick}>
            {trigger.parentExecution.name}
          </a>
        )}
        {this.getBuildLink()}
      </span>
    );
  }

  private getBuildText(execution: IExecution) {
    if (execution.trigger.linkText) {
      return <span className="build-label">{execution.trigger.linkText}</span>;
    } else {
      return (
        <>
          <span className="build-label">Build</span> #{execution.buildInfo.number}
        </>
      );
    }
  }

  private getBuildLink = () => {
    const { execution } = this.props;

    if (
      !(execution.trigger.linkText && execution.trigger.link) &&
      !(execution.buildInfo && execution.buildInfo.number)
    ) {
      return null;
    }

    if (execution.trigger.linkText && !(execution.trigger.link || execution.buildInfo.url)) {
      // If supplying link text, must supply either link or url
      return null;
    }

    if (execution.trigger.link && !(execution.trigger.linkText || execution.buildInfo.number)) {
      // If supplying link, must supply either link text or build number
      return null;
    }

    return (
      <a
        className="execution-build-number clickable"
        onClick={this.handleBuildInfoClick}
        href={execution.trigger.link ? execution.trigger.link : execution.buildInfo.url}
        target="_blank"
      >
        {this.getBuildText(execution)}
      </a>
    );
  };
}

import React from 'react';

import { IExecution } from '../../domain';
import { logger } from '../../utils';

import './ExecutionBuildLink.less';

export interface IExecutionBuildLinkProps {
  execution: IExecution;
}

export class ExecutionBuildLink extends React.Component<IExecutionBuildLinkProps, {}> {
  constructor(props: IExecutionBuildLinkProps) {
    super(props);
  }

  private handleBuildInfoClick = (event: React.MouseEvent<HTMLElement>) => {
    logger.log({ category: 'Pipeline', action: 'Execution build number clicked - build info' });
    event.stopPropagation();
  };

  public render() {
    return <span>{this.getBuildLink()}</span>;
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

import { module } from 'angular';
import React from 'react';
import { react2angular } from 'react2angular';

import { IExecutionBuildLinkProps } from './ExecutionBuildLink';
import { withErrorBoundary } from '../../presentation/SpinErrorBoundary';
import { timestamp } from '../../utils';

export interface IExecutionBuildTitleProps extends IExecutionBuildLinkProps {
  defaultToTimestamp?: boolean;
}

export class ExecutionBuildTitle extends React.Component<IExecutionBuildTitleProps> {
  public static defaultProps: Partial<IExecutionBuildTitleProps> = {
    defaultToTimestamp: false,
  };

  public render() {
    const { execution, defaultToTimestamp } = this.props;
    const hasParentPipeline = !!(execution && execution.trigger.parentPipelineName);
    const hasBuildNumber = !!(execution && execution.buildInfo && execution.buildInfo.number);
    const showBuildInfo = hasBuildNumber && !hasParentPipeline;
    const showStartTime = defaultToTimestamp && !hasParentPipeline && !hasBuildNumber;

    return (
      <span>
        {hasParentPipeline && <span>{execution.trigger.parentPipelineName}</span>}
        {showBuildInfo && (
          <span>
            <span className="build-label">Build</span> #{execution.buildInfo.number}
          </span>
        )}
        {showStartTime && <span>{timestamp(this.props.execution.startTime)}</span>}
      </span>
    );
  }
}

export const EXECUTION_BUILD_TITLE = 'spinnaker.core.pipeline.executionbuild.executionbuildtitle';
const ngmodule = module(EXECUTION_BUILD_TITLE, []);

ngmodule.component(
  'executionBuildTitle',
  react2angular(withErrorBoundary(ExecutionBuildTitle, 'executionBuildTitle'), ['execution', 'defaultToTimestamp']),
);

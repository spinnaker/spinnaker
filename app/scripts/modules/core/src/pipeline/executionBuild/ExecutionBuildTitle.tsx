import * as React from 'react';
import { BindAll } from 'lodash-decorators';

import { module } from 'angular';
import { react2angular } from 'react2angular';

import { IExecutionBuildLinkProps } from './ExecutionBuildLink';
import { timestamp } from 'core/utils';

export interface IExecutionBuildTitleProps extends IExecutionBuildLinkProps {
  defaultToTimestamp?: boolean;
}

@BindAll()
export class ExecutionBuildTitle extends React.Component<IExecutionBuildTitleProps, {}> {
  public static defaultProps: Partial<IExecutionBuildTitleProps> = {
    defaultToTimestamp: false,
  };

  private hasParentPipeline: boolean;
  private hasBuildNumber: boolean;

  constructor(props: IExecutionBuildTitleProps) {
    super(props);
    this.hasParentPipeline = !!props.execution.trigger.parentPipelineName;
    this.hasBuildNumber = !!(props.execution.buildInfo && props.execution.buildInfo.number);
  }

  public render() {
    return (
      <span>
        {this.hasParentPipeline && <span>{this.props.execution.trigger.parentPipelineName}</span>}
        {this.hasBuildNumber &&
          !this.hasParentPipeline && (
            <span>
              <span className="build-label">Build</span> #{this.props.execution.buildInfo.number}
            </span>
          )}
        {this.props.defaultToTimestamp &&
          !this.hasParentPipeline &&
          !this.hasBuildNumber && <span>{timestamp(this.props.execution.startTime)}</span>}
      </span>
    );
  }
}

export const EXECUTION_BUILD_TITLE = 'spinnaker.core.pipeline.executionbuild.executionbuildtitle';
const ngmodule = module(EXECUTION_BUILD_TITLE, []);

ngmodule.component('executionBuildTitle', react2angular(ExecutionBuildTitle, ['execution', 'defaultToTimestamp']));

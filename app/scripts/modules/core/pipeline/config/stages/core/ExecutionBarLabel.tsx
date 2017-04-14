import * as React from 'react';
import {IExecutionStageSummary} from 'core/domain/index';

export class ExecutionBarLabel extends React.Component<{ stage: IExecutionStageSummary }, any> {
  public render() {
    return (
      <span>{ this.props.stage.name ? this.props.stage.name : this.props.stage.type }</span>
    );
  }
}



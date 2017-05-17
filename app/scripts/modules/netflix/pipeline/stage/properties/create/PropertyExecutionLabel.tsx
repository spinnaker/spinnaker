import * as React from 'react';

import { IExecutionStageSummary } from '@spinnaker/core';

export class PropertyExecutionLabel extends React.Component<{ stage: IExecutionStageSummary }, any> {
  public render() {
    return (
      <span className="stage-label">
        <span>{this.props.stage.name} {this.props.stage.masterStage.context.propertyAction}</span>
      </span>
    );
  }
}

import * as React from 'react';
import { IExecutionMarkerIconProps } from '@spinnaker/core';

export class EvaluateCloudFormationChangeSetExecutionMarkerIcon extends React.Component<IExecutionMarkerIconProps> {
  constructor(props: IExecutionMarkerIconProps) {
    super(props);
  }

  public render() {
    if (
      this.props.stage.isRunning &&
      this.props.stage.stages[0].context.changeSetContainsReplacement &&
      this.props.stage.stages[0].context.actionOnReplacement === 'ask'
    ) {
      this.props.stage.requiresAttention = true;
      return <span className="fa fa-child" />;
    }
    return null;
  }
}

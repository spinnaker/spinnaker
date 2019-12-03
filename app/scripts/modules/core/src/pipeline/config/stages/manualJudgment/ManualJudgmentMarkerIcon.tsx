import React from 'react';
import { IExecutionMarkerIconProps } from '../common/ExecutionMarkerIcon';

export class ManualJudgmentMarkerIcon extends React.Component<IExecutionMarkerIconProps> {
  constructor(props: IExecutionMarkerIconProps) {
    super(props);
  }

  public render() {
    if (this.props.stage.isRunning) {
      return <span className="fa fa-child" />;
    }
    return null;
  }
}

import * as React from 'react';
import {IExecutionMarkerIconProps} from '../core/ExecutionMarkerIcon';

export class ManualJudgmentMarkerIcon extends React.Component<IExecutionMarkerIconProps, any> {

  constructor(props: IExecutionMarkerIconProps) {
    super(props);
  }

  public render() {
    if (this.props.stage.isRunning) {
      return (<span className="fa fa-child"/>);
    }
    return null;
  }
}

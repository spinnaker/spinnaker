import * as React from 'react';
import { IExecutionStageSummary } from 'core/domain/IExecutionStage';

export interface IExecutionMarkerIconProps {
  stage: IExecutionStageSummary;
}

export class ExecutionMarkerIcon extends React.Component<IExecutionMarkerIconProps, any> {
  public render() {
    return this.props.stage.inSuspendedExecutionWindow ? <span className="far fa-clock" /> : null;
  }
}

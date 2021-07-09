import React from 'react';
import { IExecutionMarkerIconProps } from '../common/ExecutionMarkerIcon';

export class GroupMarkerIcon extends React.Component<IExecutionMarkerIconProps> {
  public render() {
    return (
      <div className="marker-group-icon">
        <svg width="16" height="12" viewBox="0 0 16 16">
          <path d="M8 9l-8-4 8-4 8 4zM14.398 7.199l1.602 0.801-8 4-8-4 1.602-0.801 6.398 3.199zM14.398 10.199l1.602 0.801-8 4-8-4 1.602-0.801 6.398 3.199z" />
        </svg>
      </div>
    );
  }
}

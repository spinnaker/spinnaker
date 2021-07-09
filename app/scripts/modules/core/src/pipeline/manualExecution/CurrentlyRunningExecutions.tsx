import { head } from 'lodash';
import React from 'react';

import { IExecution } from '../../domain';
import { timestamp } from '../../utils/timeFormatters';

export interface ICurrentlyRunningExecutionsProps {
  currentlyRunningExecutions: IExecution[];
}

export class CurrentlyRunningExecutions extends React.Component<ICurrentlyRunningExecutionsProps> {
  public render() {
    const { currentlyRunningExecutions } = this.props;
    const currentlyRunningExecution = head(currentlyRunningExecutions);
    return (
      <div className="alert alert-warning">
        <p>
          <strong>
            <i className="fa fa-exclamation-triangle" />
            This pipeline is currently executing!
          </strong>
        </p>
        <div className="pad-left">
          <strong>Execution started: </strong>
          {timestamp(currentlyRunningExecution.startTime)}
          <div>
            <strong>Current stage: </strong>
            {currentlyRunningExecution.currentStages &&
              currentlyRunningExecution.currentStages.map((s: any, i: number) => {
                return <span key={i}>{s.name}</span>;
              })}
          </div>
        </div>
        {currentlyRunningExecutions.length > 1 && (
          <div>
            <em>{currentlyRunningExecutions.length - 1 + ' other execution(s)'}</em>
          </div>
        )}
      </div>
    );
  }
}

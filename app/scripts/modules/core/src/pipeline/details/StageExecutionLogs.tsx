import { get } from 'lodash';
import React from 'react';

import { IStage } from '../../domain';

export interface IStageExecutionLogsProps {
  stage: IStage;
}

export class StageExecutionLogs extends React.Component<IStageExecutionLogsProps> {
  public render() {
    const logs = get<string>(this.props.stage, 'context.execution.logs');
    return logs ? (
      <div className="row">
        <div className="col-md-12">
          <div className="well alert alert-info">
            <a target="_blank" href={logs}>
              View Execution Logs
            </a>
          </div>
        </div>
      </div>
    ) : null;
  }
}

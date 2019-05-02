import * as React from 'react';
import { get } from 'lodash';

import { IStage } from 'core/domain';

export interface IStageExecutionLogsProps {
  stage: IStage;
}

export class StageExecutionLogs extends React.Component<IStageExecutionLogsProps> {
  public render() {
    const logs = get<string>(this.props.stage, 'context.execution.logs');
    return (
      <div className="row">
        <div className="col-md-12">
          <div className="well alert alert-info">
            <a target="_blank" href={logs}>
              View Execution Logs
            </a>
          </div>
        </div>
      </div>
    );
  }
}

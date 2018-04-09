import * as React from 'react';
import { get } from 'lodash';

import { IStage } from 'core/domain';

export const StageExecutionLogs = (props: { stage: IStage }): JSX.Element => {
  const logs = get<string>(props.stage, 'context.execution.logs');
  if (!logs) {
    return null;
  }

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
};

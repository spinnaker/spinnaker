import React from 'react';

import type { IExecutionDetailsSectionProps } from '../common';
import { ExecutionDetailsSection } from '../common';
import { StageFailureMessage } from '../../../details';

export function WaitForParentTasksExecutionDetails(props: IExecutionDetailsSectionProps) {
  const { stage } = props;
  return (
    <ExecutionDetailsSection name={props.name} current={props.current}>
      <div className="row">
        <div className="col-md-12">
          <h5>Parent Tasks</h5>
        </div>
      </div>
      {(stage.parentTasks || []).map((task: any, index: number) => (
        <div className="row" key={index} style={{ marginBottom: 10 }}>
          <div className="col-md-6">{task.name}</div>
          <div className="col-md-6">
            <span className={`label label-default label-${(task.status || '').toLowerCase()}`}>{task.status}</span>
          </div>
        </div>
      ))}
      <StageFailureMessage stage={stage} message={stage.failureMessage} />
    </ExecutionDetailsSection>
  );
}

WaitForParentTasksExecutionDetails.title = 'parentTasks';

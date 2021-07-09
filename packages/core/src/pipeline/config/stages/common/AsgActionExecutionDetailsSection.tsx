import React from 'react';

import { ExecutionDetailsSection, IExecutionDetailsSectionProps } from './';
import { AccountTag } from '../../../../account';
import { StageExecutionLogs, StageFailureMessage } from '../../../details';

export function AsgActionExecutionDetailsSection(props: IExecutionDetailsSectionProps & { action: string }) {
  const { action, stage } = props;
  return (
    <ExecutionDetailsSection name={props.name} current={props.current}>
      <div className="row">
        <div className="col-md-9">
          <dl className="dl-narrow dl-horizontal">
            <dt>Account</dt>
            <dd>
              <AccountTag account={stage.context.credentials} />
            </dd>
            <dt>Region</dt>
            <dd>{stage.context.region}</dd>
            <dt>Server Group</dt>
            <dd>{stage.context.serverGroupName}</dd>
          </dl>
        </div>
      </div>
      <StageFailureMessage stage={stage} message={stage.failureMessage} />
      {stage.isCompleted && stage.context.serverGroupName && (
        <div className="row">
          <div className="col-md-12">
            <div className="well alert alert-info">
              <strong>{action}: </strong>
              {stage.context.serverGroupName}
            </div>
          </div>
        </div>
      )}
      <StageExecutionLogs stage={stage} />
    </ExecutionDetailsSection>
  );
}

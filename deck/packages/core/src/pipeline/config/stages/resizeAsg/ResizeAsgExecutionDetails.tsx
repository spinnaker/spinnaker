import React from 'react';

import { AccountTag } from '../../../../account';
import type { IExecutionDetailsSectionProps } from '../common';
import { ExecutionDetailsSection } from '../common';
import { StageFailureMessage } from '../../../details';

function isHttpUrl(url: string): boolean {
  try {
    const parsed = new URL(url);
    return parsed.protocol === 'http:' || parsed.protocol === 'https:';
  } catch (_e) {
    return false;
  }
}

export function ResizeAsgExecutionDetails(props: IExecutionDetailsSectionProps) {
  const { stage } = props;
  const capacity = stage.context?.capacity || {};
  const logsUrl = stage.context?.execution?.logs;
  return (
    <ExecutionDetailsSection name={props.name} current={props.current}>
      <div className="row">
        <div className="col-md-9">
          <dl className="dl-narrow dl-horizontal">
            <dt>Account</dt>
            <dd>
              <AccountTag account={stage.context?.credentials} />
            </dd>
            <dt>Region</dt>
            <dd>{stage.context?.region}</dd>
            <dt>Server Group</dt>
            <dd>{stage.context?.serverGroupName}</dd>
            <dt>Capacity</dt>
            <dd>
              Min: {capacity.min} / Desired: {capacity.desired} / Max: {capacity.max}
            </dd>
          </dl>
        </div>
      </div>
      <StageFailureMessage stage={stage} message={stage.failureMessage} />
      {logsUrl && isHttpUrl(logsUrl) && (
        <div className="row">
          <div className="col-md-12">
            <div className="well alert alert-info">
              <a target="_blank" href={logsUrl} rel="noopener noreferrer">
                View Execution Logs
              </a>
            </div>
          </div>
        </div>
      )}
    </ExecutionDetailsSection>
  );
}

ResizeAsgExecutionDetails.title = 'resizeServerGroupConfig';

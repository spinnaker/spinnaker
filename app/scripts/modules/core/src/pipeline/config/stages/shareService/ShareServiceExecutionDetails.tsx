import React from 'react';

import { AccountTag } from '../../../../account';

import { ExecutionDetailsSection, IExecutionDetailsSectionProps } from '../common';
import { StageFailureMessage } from '../../../details';

export function ShareServiceExecutionDetails(props: IExecutionDetailsSectionProps) {
  const { stage } = props;
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
          </dl>
        </div>
      </div>
      <StageFailureMessage stage={props.stage} message={props.stage.failureMessage} />
    </ExecutionDetailsSection>
  );
}

// TODO: refactor this to not use namespace
// eslint-disable-next-line
export namespace ShareServiceExecutionDetails {
  export const title = 'shareServiceConfig';
}

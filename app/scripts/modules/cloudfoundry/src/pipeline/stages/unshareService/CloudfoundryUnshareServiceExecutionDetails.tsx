import { get } from 'lodash';
import React from 'react';

import {
  AccountTag,
  ExecutionDetailsSection,
  IExecutionDetailsSectionProps,
  StageExecutionLogs,
  StageFailureMessage,
} from '@spinnaker/core';

export function CloudfoundryUnshareServiceExecutionDetails(props: IExecutionDetailsSectionProps) {
  const { stage } = props;
  const { context } = stage;
  const account = get(context, 'service.account', undefined);
  const serviceInstanceName = get(context, 'serviceInstanceName', undefined);
  const unshareFromRegions = get(context, 'unshareFromRegions', undefined);
  return (
    <ExecutionDetailsSection name={props.name} current={props.current}>
      <div className="step-section-details">
        <div className="row">
          <div className="col-md-12">
            <dl className="dl-horizontal">
              <dt>Account</dt>
              <dd>
                <AccountTag account={account} />
              </dd>
              <dt>Service Instance Name</dt>
              <dd>
                {serviceInstanceName}
                <br />
              </dd>
              <dt>Unsharing Regions</dt>
              <dd>
                {unshareFromRegions.join(', ')}
                <br />
              </dd>
            </dl>
          </div>
        </div>
      </div>
      <StageFailureMessage stage={stage} message={stage.failureMessage} />
      <StageExecutionLogs stage={stage} />
    </ExecutionDetailsSection>
  );
}

// eslint-disable-next-line
export namespace CloudfoundryUnshareServiceExecutionDetails {
  export const title = 'cloudfoundryUnshareServiceConfig';
}

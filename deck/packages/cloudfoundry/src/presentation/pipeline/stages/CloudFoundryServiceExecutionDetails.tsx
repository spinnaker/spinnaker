import { get } from 'lodash';
import React from 'react';

import type { IExecutionDetailsSectionProps } from '@spinnaker/core';
import { AccountTag, ExecutionDetailsSection, StageExecutionLogs, StageFailureMessage } from '@spinnaker/core';

export function CloudFoundryServiceExecutionDetails(props: IExecutionDetailsSectionProps) {
  const { stage } = props;
  const { context } = stage;
  const account = get(context, 'service.account', undefined);
  const region = get(context, 'service.region', undefined);
  const serviceInstanceName = get(context, 'service.instance.name', undefined);
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
              <dt>Region</dt>
              <dd>
                {region}
                <br />
              </dd>
              <dt>Service Instance Name</dt>
              <dd>
                {serviceInstanceName}
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
export namespace CloudFoundryServiceExecutionDetails {
  export const title = 'cloudfoundryServiceConfig';
}

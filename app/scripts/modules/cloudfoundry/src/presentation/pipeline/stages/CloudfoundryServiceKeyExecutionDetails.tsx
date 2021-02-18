import { get } from 'lodash';
import React from 'react';

import {
  AccountTag,
  ExecutionDetailsSection,
  IExecutionDetailsSectionProps,
  StageExecutionLogs,
  StageFailureMessage,
} from '@spinnaker/core';

export function CloudfoundryServiceKeyExecutionDetails(props: IExecutionDetailsSectionProps) {
  const { stage } = props;
  const { context } = stage;
  const account = get(context, 'service.account', undefined);
  const region = get(context, 'service.region', undefined);
  const serviceInstanceName = get(context, 'serviceInstanceName', undefined);
  const serviceKeyName = get(context, 'serviceKeyName', undefined);
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
              <dt>Service Key Name</dt>
              <dd>
                {serviceKeyName}
                <br />
              </dd>
            </dl>
          </div>
        </div>
      </div>
      <StageFailureMessage stage={props.stage} message={props.stage.failureMessage} />
      <StageExecutionLogs stage={props.stage} />
    </ExecutionDetailsSection>
  );
}

// eslint-disable-next-line
export namespace CloudfoundryServiceKeyExecutionDetails {
  export const title = 'cloudfoundryServiceKeyConfig';
}

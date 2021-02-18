import { get } from 'lodash';
import React from 'react';

import {
  AccountTag,
  ExecutionDetailsSection,
  IExecutionDetailsSectionProps,
  StageExecutionLogs,
  StageFailureMessage,
} from '@spinnaker/core';

export function CloudfoundryLoadBalancersExecutionDetails(props: IExecutionDetailsSectionProps) {
  const { stage } = props;
  const { context } = stage;
  const account = get(context, 'credentials', undefined);
  const region = get(context, 'region', undefined);
  const loadBalancerNames = get(context, 'loadBalancerNames', undefined);
  const serverGroupName = get(context, 'serverGroupName', undefined);
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
              <dt>Server Group Name</dt>
              <dd>
                {serverGroupName}
                <br />
              </dd>
              <dt>Load Balancers</dt>
              <dd>
                {loadBalancerNames.join(', ')}
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

// TODO: refactor this to not use namespace
// eslint-disable-next-line
export namespace CloudfoundryLoadBalancersExecutionDetails {
  export const title = 'cloudfoundryLoadBalancersConfig';
}

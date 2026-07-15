import React from 'react';

import type { IExecutionDetailsSectionProps } from '@spinnaker/core';
import { ExecutionDetailsSection } from '@spinnaker/core';

function FailureMessages({ stage }: Pick<IExecutionDetailsSectionProps, 'stage'>) {
  const failures = (stage.context?.exception?.details?.errors || stage.context?.failureMessages || []) as string[];
  if (!failures.length) {
    return null;
  }

  return (
    <div className="alert alert-danger">
      {failures.map((message, index) => (
        <p key={index}>{message}</p>
      ))}
    </div>
  );
}

export function getServerGroupDisplayName(context: any): string {
  return context.cluster || context.serverGroupName;
}

function ServerGroupConfigDetails(props: IExecutionDetailsSectionProps) {
  const { stage } = props;
  return (
    <div>
      <dl className="dl-horizontal dl-narrow">
        <dt>Account</dt>
        <dd>{stage.context.credentials || stage.context.account}</dd>
        <dt>Region</dt>
        <dd>{stage.context.region}</dd>
        <dt>{stage.context.cluster ? 'Cluster' : 'Server Group'}</dt>
        <dd>{getServerGroupDisplayName(stage.context)}</dd>
        {stage.context.target && [
          <dt key="target-label">Target</dt>,
          <dd key="target-value">{stage.context.target}</dd>,
        ]}
      </dl>
      <FailureMessages stage={stage} />
    </div>
  );
}

export function AppengineStartServerGroupExecutionDetails(props: IExecutionDetailsSectionProps) {
  return (
    <ExecutionDetailsSection name={AppengineStartServerGroupExecutionDetails.title} current={props.current}>
      <ServerGroupConfigDetails {...props} />
    </ExecutionDetailsSection>
  );
}
AppengineStartServerGroupExecutionDetails.title = 'startServerGroupConfig';

export function AppengineStopServerGroupExecutionDetails(props: IExecutionDetailsSectionProps) {
  return (
    <ExecutionDetailsSection name={AppengineStopServerGroupExecutionDetails.title} current={props.current}>
      <ServerGroupConfigDetails {...props} />
    </ExecutionDetailsSection>
  );
}
AppengineStopServerGroupExecutionDetails.title = 'stopServerGroupConfig';

export function AppengineEditLoadBalancerExecutionDetails(props: IExecutionDetailsSectionProps) {
  const loadBalancers = props.stage.context.loadBalancers || [];
  return (
    <ExecutionDetailsSection name={AppengineEditLoadBalancerExecutionDetails.title} current={props.current}>
      <table className="table table-condensed">
        <thead>
          <tr>
            <th>Account</th>
            <th>Name</th>
            <th>Region</th>
          </tr>
        </thead>
        <tbody>
          {loadBalancers.map((loadBalancer: any, index: number) => (
            <tr key={index}>
              <td>{loadBalancer.credentials || loadBalancer.account}</td>
              <td>{loadBalancer.name || loadBalancer.loadBalancerName}</td>
              <td>{loadBalancer.region}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <FailureMessages stage={props.stage} />
    </ExecutionDetailsSection>
  );
}
AppengineEditLoadBalancerExecutionDetails.title = 'editLoadBalancerConfig';

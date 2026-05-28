import React from 'react';

import type { IExecutionDetailsSectionProps, ILoadBalancer } from '@spinnaker/core';
import { AccountTag, ExecutionDetailsSection, StageFailureMessage } from '@spinnaker/core';

import type { ICloudrunAllocationDescription } from '../../../loadBalancer/loadBalancerTransformer';

export function CloudrunEditLoadBalancerExecutionDetails(props: IExecutionDetailsSectionProps) {
  const { current, name, stage } = props;
  const loadBalancers = ((stage.context || {}).loadBalancers || []) as ILoadBalancer[];

  return (
    <ExecutionDetailsSection name={name} current={current}>
      <div className="row">
        <div className="col-md-12">
          <table className="table table-condensed">
            <thead>
              <tr>
                <th>Account</th>
                <th>Name</th>
                <th>Region</th>
                <th>Allocations</th>
              </tr>
            </thead>
            <tbody>
              {loadBalancers.map((loadBalancer) => (
                <tr key={getLoadBalancerCompositeKey(loadBalancer)}>
                  <td>
                    <AccountTag account={loadBalancer.credentials || loadBalancer.account} />
                  </td>
                  <td>{loadBalancer.name}</td>
                  <td>{loadBalancer.region}</td>
                  <td>{renderAllocations((loadBalancer as any).splitDescription?.allocationDescriptions)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
      <StageFailureMessage stage={stage} message={stage.failureMessage} />
    </ExecutionDetailsSection>
  );
}

CloudrunEditLoadBalancerExecutionDetails.title = 'editLoadBalancerConfig';

export function getLoadBalancerCompositeKey(loadBalancer: Partial<ILoadBalancer>): string {
  return [
    loadBalancer.name || '',
    loadBalancer.credentials || loadBalancer.account || '',
    loadBalancer.region || '',
  ].join(':');
}

function renderAllocations(allocationDescriptions: ICloudrunAllocationDescription[] = []): React.ReactNode {
  if (!allocationDescriptions.length) {
    return null;
  }

  return (
    <ul className="list-unstyled no-margin">
      {allocationDescriptions.map((allocation, index) => (
        <li key={`${allocation.revisionName || allocation.target || allocation.cluster || 'allocation'}:${index}`}>
          {allocation.revisionName || allocation.target || allocation.cluster || 'Allocation'}:{' '}
          <span>{allocation.percent}%</span>
        </li>
      ))}
    </ul>
  );
}

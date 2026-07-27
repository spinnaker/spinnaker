import React from 'react';

import type { ILoadBalancerDetailsSectionProps } from '@spinnaker/core';

import type { IAppengineLoadBalancer } from '../../domain';

export function AppengineLoadBalancerDetailsSection({ loadBalancer }: ILoadBalancerDetailsSectionProps) {
  const appengineLoadBalancer = loadBalancer as IAppengineLoadBalancer;
  const allocations = appengineLoadBalancer.split?.allocations || {};

  return (
    <div className="content-section">
      <div className="content-section-heading">Traffic Split</div>
      <dl className="dl-horizontal dl-narrow">
        <dt>Account</dt>
        <dd>{appengineLoadBalancer.account || appengineLoadBalancer.credentials}</dd>
        <dt>Region</dt>
        <dd>{appengineLoadBalancer.region}</dd>
        <dt>Shard By</dt>
        <dd>{appengineLoadBalancer.split?.shardBy || 'Unspecified'}</dd>
      </dl>
      {Object.keys(allocations).length > 0 && (
        <table className="table table-condensed">
          <thead>
            <tr>
              <th>Server Group</th>
              <th>Allocation</th>
            </tr>
          </thead>
          <tbody>
            {Object.entries(allocations).map(([serverGroup, allocation]) => (
              <tr key={serverGroup}>
                <td>{serverGroup}</td>
                <td>{Math.round(allocation * 1000) / 10}%</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}

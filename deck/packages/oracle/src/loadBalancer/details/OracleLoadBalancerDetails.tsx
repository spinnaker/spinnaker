import React, { useEffect } from 'react';
import { AccountTag, CollapsibleSection, useDataSource } from '@spinnaker/core';

export function useOracleLoadBalancerDetails({ app, loadBalancerParams, autoClose }: any) {
  const { data: loadBalancers, loaded, refresh, error: loadBalancersError } = useDataSource<any[]>(
    app.getDataSource('loadBalancers'),
  );
  const loading = !loaded;
  const loadBalancer = loading
    ? undefined
    : (loadBalancers || []).find(
        (test: any) =>
          test.name === loadBalancerParams.name &&
          test.region === loadBalancerParams.region &&
          test.account === loadBalancerParams.accountId,
      );

  useEffect(() => {
    if (!loading && !loadBalancersError && !loadBalancer) {
      autoClose();
    }
  }, [autoClose, loadBalancer, loadBalancersError, loading]);

  return {
    data: loadBalancer,
    loading,
    error: loadBalancersError || (!loading && !loadBalancer ? 'Load balancer not found' : undefined),
    refetch: refresh,
  };
}

function OracleLoadBalancerInformationSection({ loadBalancer }: any) {
  return (
    <CollapsibleSection heading="Load Balancer Details" defaultExpanded={true}>
      <dl className="dl-horizontal dl-narrow">
        <dt>Created</dt>
        <dd>{loadBalancer.timeCreated}</dd>
        <dt>In</dt>
        <dd>
          <AccountTag account={loadBalancer.account} /> {loadBalancer.region}
        </dd>
        <dt>Subnets</dt>
        <dd>{(loadBalancer.subnets || []).map((subnet: any) => subnet.name).join(', ')}</dd>
        <dt>Public IP</dt>
        <dd>{(loadBalancer.ipAddresses || []).map((ip: any) => ip.ipAddress).join(', ')}</dd>
      </dl>
    </CollapsibleSection>
  );
}

function OracleLoadBalancerBackendSetsSection({ loadBalancer }: any) {
  return (
    <CollapsibleSection heading="Backend Sets">
      {(Object.values(loadBalancer.backendSets || {}) as any[]).map((backendSet: any) => (
        <dl key={backendSet.name || backendSet.policy}>
          <dt>{backendSet.name}</dt>
          <dd>{backendSet.policy}</dd>
          <dd>Health Check: {backendSet.healthChecker?.urlPath}</dd>
        </dl>
      ))}
    </CollapsibleSection>
  );
}

function OracleLoadBalancerListenersSection({ loadBalancer }: any) {
  return (
    <CollapsibleSection heading="Listeners">
      <dl>
        <dt>Listener - Backend Set</dt>
        {(Object.values(loadBalancer.listeners || {}) as any[]).map((listener: any) => (
          <dd key={`${listener.protocol}-${listener.port}`}>
            {listener.protocol}:{listener.port} - {listener.defaultBackendSetName}
          </dd>
        ))}
      </dl>
    </CollapsibleSection>
  );
}

export const OracleLoadBalancerDetailsSections = [
  OracleLoadBalancerInformationSection,
  OracleLoadBalancerBackendSetsSection,
  OracleLoadBalancerListenersSection,
];

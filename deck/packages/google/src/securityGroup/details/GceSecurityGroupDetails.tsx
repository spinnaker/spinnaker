import React, { useEffect, useState } from 'react';

import { AccountTag, CollapsibleSection, FirewallLabels, ReactInjector } from '@spinnaker/core';

interface IGceSecurityGroupDetailsProps {
  app: any;
  resolvedSecurityGroup: any;
}

function parseList(value: any): string[] {
  if (Array.isArray(value)) {
    return value;
  }
  if (typeof value === 'string' && value.startsWith('[') && value.endsWith(']')) {
    return value
      .slice(1, -1)
      .split(',')
      .map((item) => item.trim())
      .filter(Boolean);
  }
  return value ? [value] : [];
}

export function GceSecurityGroupDetails({ app, resolvedSecurityGroup }: IGceSecurityGroupDetailsProps): JSX.Element {
  const [securityGroup, setSecurityGroup] = useState<any>(resolvedSecurityGroup || {});
  const [loading, setLoading] = useState<boolean>(true);

  useEffect(() => {
    let cancelled = false;

    ReactInjector.securityGroupReader
      .getSecurityGroupDetails(
        app,
        resolvedSecurityGroup.accountId,
        resolvedSecurityGroup.provider,
        resolvedSecurityGroup.region,
        resolvedSecurityGroup.vpcId,
        resolvedSecurityGroup.name,
      )
      .then(
        (details: any) => {
          if (cancelled) {
            return;
          }
          setSecurityGroup({ ...resolvedSecurityGroup, ...details });
          setLoading(false);
        },
        () => {
          if (!cancelled) {
            setLoading(false);
          }
        },
      );

    return () => {
      cancelled = true;
    };
  }, [app, resolvedSecurityGroup]);

  const targetTags = parseList(securityGroup.targetTags);
  const sourceTags = parseList(securityGroup.sourceTags);
  const sourceRanges = parseList(securityGroup.sourceRanges).concat(
    (securityGroup.ipRangeRules || []).map((rule: any) => `${rule.range?.ip || ''}${rule.range?.cidr || ''}`),
  );

  return (
    <div className="details-panel">
      <div className="header">
        <div className="header-text horizontal middle">
          <span className="icon-gce" />
          <h3>{securityGroup.name}</h3>
        </div>
      </div>
      <div className="content">
        {loading && <div className="text-center">Loading...</div>}
        <CollapsibleSection heading={`${FirewallLabels.get('Firewall')} Information`} defaultExpanded={true}>
          <dl className="dl-horizontal dl-narrow">
            <dt>Account</dt>
            <dd>
              <AccountTag account={securityGroup.accountId || securityGroup.accountName} />
            </dd>
            <dt>Region</dt>
            <dd>{securityGroup.region || 'global'}</dd>
            <dt>Network</dt>
            <dd>{securityGroup.vpcId || securityGroup.network || '-'}</dd>
            <dt>Description</dt>
            <dd>{securityGroup.description || '-'}</dd>
          </dl>
        </CollapsibleSection>
        <CollapsibleSection heading="Targets" defaultExpanded={true}>
          <p>{targetTags.join(', ') || 'All traffic'}</p>
        </CollapsibleSection>
        <CollapsibleSection heading="Sources" defaultExpanded={true}>
          <p>{sourceTags.concat(sourceRanges).join(', ') || '-'}</p>
        </CollapsibleSection>
        <CollapsibleSection heading="Allowed Ingress" defaultExpanded={true}>
          {(securityGroup.inboundRules || securityGroup.ipIngressRules || []).length ? (
            <ul>
              {(securityGroup.inboundRules || securityGroup.ipIngressRules || []).map((rule: any, index: number) => (
                <li key={index}>
                  {[rule.protocol, rule.portRanges?.map((range: any) => range.startPort).join(', ')]
                    .filter(Boolean)
                    .join(' ')}
                </li>
              ))}
            </ul>
          ) : (
            <span>-</span>
          )}
        </CollapsibleSection>
      </div>
    </div>
  );
}

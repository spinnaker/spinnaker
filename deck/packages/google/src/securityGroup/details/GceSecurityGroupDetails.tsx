import React, { useEffect, useState } from 'react';

import {
  AccountTag,
  CollapsibleSection,
  ConfirmationModalService,
  FirewallLabels,
  SecurityGroupWriter,
  useDeckRuntimeServices,
} from '@spinnaker/core';

import { GceSecurityGroupModal } from '../configure/GceSecurityGroupModal';

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

function withResolvedCoordinates(securityGroup: any, resolvedSecurityGroup?: any): any {
  if (!resolvedSecurityGroup) {
    return securityGroup;
  }
  const accountId =
    securityGroup.accountId ||
    securityGroup.accountName ||
    securityGroup.account ||
    securityGroup.credentials ||
    resolvedSecurityGroup.accountId;
  return {
    ...securityGroup,
    accountId,
    accountName: securityGroup.accountName || accountId,
    name: securityGroup.name || resolvedSecurityGroup.name,
    provider: securityGroup.provider || resolvedSecurityGroup.provider || 'gce',
    region: securityGroup.region || resolvedSecurityGroup.region || 'global',
    vpcId: securityGroup.vpcId || resolvedSecurityGroup.vpcId,
  };
}

function securityGroupCoordinatesKey(securityGroup: any): string {
  return [
    securityGroup.accountId,
    securityGroup.provider,
    securityGroup.region,
    securityGroup.vpcId || '',
    securityGroup.name,
  ].join(':');
}

export function GceSecurityGroupActions({
  app,
  resolvedSecurityGroup,
  securityGroup,
}: {
  app: any;
  resolvedSecurityGroup?: any;
  securityGroup: any;
}): JSX.Element {
  const runtimeServices = useDeckRuntimeServices();
  const [menuOpen, setMenuOpen] = useState(false);
  const firewall = withResolvedCoordinates(securityGroup, resolvedSecurityGroup);
  const sharedVpcHostFirewall = typeof firewall.id === 'string' && firewall.id.includes('/');
  const readOnlyExplanation = 'You cannot modify shared VPC host project firewall rules.';
  const editInboundRules = (): void => {
    GceSecurityGroupModal.show({ application: app, mode: 'edit', securityGroup: firewall }, runtimeServices);
  };
  const cloneSecurityGroup = (): void => {
    GceSecurityGroupModal.show({ application: app, mode: 'clone', securityGroup: firewall }, runtimeServices);
  };
  const deleteSecurityGroup = (): void => {
    ConfirmationModalService.confirm({
      account: firewall.accountId,
      buttonText: `Delete ${firewall.name}`,
      header: `Really delete ${firewall.name}?`,
      submitMethod: () =>
        SecurityGroupWriter.deleteSecurityGroup(firewall, app, {
          cloudProvider: 'gce',
          securityGroupName: firewall.name,
        } as any),
      taskMonitorConfig: {
        application: app,
        title: `Deleting ${firewall.name}`,
      },
    });
  };

  return (
    <div className="actions">
      <div className={`dropdown ${menuOpen ? 'open' : ''}`} id="gce-security-group-actions-dropdown">
        <button
          aria-expanded={menuOpen}
          className="btn btn-sm btn-primary dropdown-toggle"
          onClick={() => setMenuOpen(!menuOpen)}
          type="button"
        >
          Firewall Actions <span className="caret" />
        </button>
        <ul className="dropdown-menu" role="menu">
          <li className={sharedVpcHostFirewall ? 'disabled' : undefined}>
            <button
              className="btn btn-link"
              data-action="edit"
              disabled={sharedVpcHostFirewall}
              onClick={editInboundRules}
              title={sharedVpcHostFirewall ? readOnlyExplanation : undefined}
              type="button"
            >
              Edit Inbound Rules
            </button>
          </li>
          <li className={sharedVpcHostFirewall ? 'disabled' : undefined}>
            <button
              className="btn btn-link"
              data-action="clone"
              disabled={sharedVpcHostFirewall}
              onClick={cloneSecurityGroup}
              title={sharedVpcHostFirewall ? readOnlyExplanation : undefined}
              type="button"
            >
              Clone Firewall
            </button>
          </li>
          <li className={sharedVpcHostFirewall ? 'disabled' : undefined}>
            <button
              className="btn btn-link"
              data-action="delete"
              disabled={sharedVpcHostFirewall}
              onClick={deleteSecurityGroup}
              title={sharedVpcHostFirewall ? readOnlyExplanation : undefined}
              type="button"
            >
              Delete Firewall
            </button>
          </li>
        </ul>
      </div>
      {sharedVpcHostFirewall && <div className="shared-vpc-warning help-block">{readOnlyExplanation}</div>}
    </div>
  );
}

export function GceSecurityGroupDetails({ app, resolvedSecurityGroup }: IGceSecurityGroupDetailsProps): JSX.Element {
  const { securityGroupReader } = useDeckRuntimeServices();
  const routeKey = securityGroupCoordinatesKey(resolvedSecurityGroup);
  const [loadedSecurityGroup, setLoadedSecurityGroup] = useState<{ key: string; value: any }>();

  useEffect(() => {
    let cancelled = false;

    const loadSecurityGroup = (): PromiseLike<any> => {
      return securityGroupReader
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
            if (!cancelled) {
              setLoadedSecurityGroup({ key: routeKey, value: { ...resolvedSecurityGroup, ...details } });
            }
          },
          () => {
            if (!cancelled) {
              setLoadedSecurityGroup({ key: routeKey, value: resolvedSecurityGroup });
            }
          },
        );
    };

    loadSecurityGroup();
    const unsubscribeFromRefresh = app.securityGroups?.onRefresh?.(null, loadSecurityGroup);

    return () => {
      cancelled = true;
      unsubscribeFromRefresh?.();
    };
  }, [app, routeKey, securityGroupReader]);

  const loading = loadedSecurityGroup?.key !== routeKey;
  const securityGroup = loading ? resolvedSecurityGroup : loadedSecurityGroup.value;

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
        {!loading && (
          <GceSecurityGroupActions
            app={app}
            resolvedSecurityGroup={resolvedSecurityGroup}
            securityGroup={securityGroup}
          />
        )}
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

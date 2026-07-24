import { UISref } from '@uirouter/react';
import { head, sortBy } from 'lodash';
import React from 'react';
import { Dropdown, MenuItem } from 'react-bootstrap';

import type {
  Application,
  IApplicationSecurityGroup,
  ILoadBalancer,
  ILoadBalancerActionsProps,
  ILoadBalancerDetailsSectionProps,
  IUseDetailsHookProps,
  SecurityGroupReader,
  UseDetailsResult,
} from '@spinnaker/core';
import {
  AccountTag,
  CollapsibleSection,
  CopyToClipboard,
  FirewallLabels,
  HealthCounts,
  ManagedResourceDetailsIndicator,
  REST,
  SubnetReader,
  timestamp,
  useDataSource,
  useDeckRuntimeServices,
} from '@spinnaker/core';

import { LoadBalancerActions } from './LoadBalancerActions';
import type {
  IAmazonApplicationLoadBalancer,
  IAmazonLoadBalancer,
  IAmazonLoadBalancerSourceData,
  IApplicationLoadBalancerSourceData,
  IClassicLoadBalancerSourceData,
  IListenerAction,
  ITargetGroup,
} from '../../domain';
import { VpcTag } from '../../vpc/VpcTag';

type ILoadBalancerStateParams = IUseDetailsHookProps['loadBalancerParams'];

interface ILoadAmazonLoadBalancerDetailsProps {
  app: Application;
  autoClose: () => void;
  loadBalancerParams: ILoadBalancerStateParams;
  loadBalancers?: ILoadBalancer[];
  securityGroupReader?: SecurityGroupReader;
}

interface IActionDetails extends IListenerAction {
  targetGroup?: ITargetGroup;
}

function ipAddressTypeDescription(ipAddressType: string): string {
  if (ipAddressType === 'dualstack') {
    return 'IPv4 and IPv6';
  }
  if (ipAddressType === 'ipv4') {
    return 'IPv4';
  }
  return '';
}

function getElbProtocol(loadBalancer: IAmazonLoadBalancer, details: IAmazonLoadBalancerSourceData): string {
  const classic = details as IClassicLoadBalancerSourceData;
  if (classic.listenerDescriptions?.some((listener: any) => listener.listener.protocol === 'HTTPS')) {
    return 'https:';
  }
  const applicationLoadBalancer = loadBalancer as IAmazonApplicationLoadBalancer;
  if (applicationLoadBalancer.listeners?.some((listener) => listener.protocol === 'HTTPS')) {
    return 'https:';
  }
  return 'http:';
}

function buildListenerRows(
  loadBalancer: IAmazonApplicationLoadBalancer,
): Array<{ in: string; actions: IActionDetails[] }> {
  const listeners = loadBalancer.listeners || [];
  const rows: Array<{ in: string; actions: IActionDetails[] }> = [];

  listeners.forEach((listener) => {
    (listener.rules || []).forEach((rule) => {
      let inMatch = [
        listener.protocol,
        (rule.conditions.find((condition) => condition.field === 'host-header') || { values: [''] }).values[0],
        listener.port,
      ]
        .filter((value) => value)
        .join(':');
      const path = (rule.conditions.find((condition) => condition.field === 'path-pattern') || { values: [] })
        .values[0];
      if (path) {
        inMatch = `${inMatch}${path}`;
      }
      const actions = (rule.actions || []).map((action) => {
        const actionDetails = { ...action } as IActionDetails;
        if (actionDetails.type === 'forward') {
          actionDetails.targetGroup = (loadBalancer.targetGroups || []).find(
            (targetGroup) => targetGroup.name === actionDetails.targetGroupName,
          );
        }
        return actionDetails;
      });
      rows.push({ in: inMatch, actions });
    });
  });

  return rows;
}

function resolveSecurityGroups(
  app: Application,
  loadBalancer: IAmazonLoadBalancer,
  params: ILoadBalancerStateParams,
  securityGroupReader?: SecurityGroupReader,
): IApplicationSecurityGroup[] {
  const securityGroupIds = loadBalancer.elb?.securityGroups || loadBalancer.securityGroups || [];
  return securityGroupIds
    .map((securityGroupId: string) => {
      if (securityGroupReader) {
        return securityGroupReader.getApplicationSecurityGroup(app, params.accountId, params.region, securityGroupId);
      }
      return app['securityGroupsIndex']?.[params.accountId]?.[params.region]?.[securityGroupId];
    })
    .filter(Boolean)
    .sort((a: any, b: any) => a.name.localeCompare(b.name));
}

async function loadSubnetDetails(loadBalancer: IAmazonLoadBalancer): Promise<void> {
  if (!loadBalancer.subnets?.length) {
    loadBalancer.subnetDetails = loadBalancer.subnetDetails || [];
    return;
  }

  loadBalancer.subnetDetails = await Promise.all(
    loadBalancer.subnets.map((subnetId) => SubnetReader.getSubnetByIdAndProvider(subnetId, loadBalancer.provider)),
  );
}

export async function loadAmazonLoadBalancerDetails({
  app,
  autoClose,
  loadBalancerParams,
  loadBalancers,
  securityGroupReader,
}: ILoadAmazonLoadBalancerDetailsProps): Promise<IAmazonLoadBalancer | undefined> {
  const { accountId, name, region } = loadBalancerParams;
  const loadBalancer =
    ((loadBalancers || app.loadBalancers.data).find(
      (candidate: ILoadBalancer) =>
        candidate.name === name && candidate.region === region && candidate.account === accountId,
    ) as IAmazonLoadBalancer) || undefined;

  if (!loadBalancer) {
    autoClose();
    return undefined;
  }

  const details = (await REST('/loadBalancers')
    .path(accountId, region, name)
    .query({ provider: 'aws' })
    .get()) as IAmazonLoadBalancerSourceData[];
  if (details.length) {
    loadBalancer.elb = details[0];
    loadBalancer.elb.vpcId = loadBalancer.elb.vpcId || loadBalancer.elb.vpcid;
    loadBalancer.account = accountId;
    loadBalancer.securityGroups = resolveSecurityGroups(
      app,
      loadBalancer,
      loadBalancerParams,
      securityGroupReader,
    ) as any;
    loadBalancer.elbProtocol = getElbProtocol(loadBalancer, details[0]);
    loadBalancer.ipAddressTypeDescription = ipAddressTypeDescription(
      (details[0] as IApplicationLoadBalancerSourceData).ipAddressType,
    );
    loadBalancer.listenerRows =
      loadBalancer.loadBalancerType === 'classic'
        ? []
        : buildListenerRows(loadBalancer as IAmazonApplicationLoadBalancer);
    await loadSubnetDetails(loadBalancer);
  }

  return loadBalancer;
}

export function useAmazonLoadBalancerDetails({
  app,
  loadBalancerParams,
  autoClose,
}: IUseDetailsHookProps): UseDetailsResult<ILoadBalancer> {
  const { securityGroupReader } = useDeckRuntimeServices();
  const dataSource = app.getDataSource('loadBalancers');
  const { data: loadBalancers, loaded, refresh, error } = useDataSource<ILoadBalancer[]>(dataSource);
  const [data, setData] = React.useState<IAmazonLoadBalancer>();
  const [loadingDetails, setLoadingDetails] = React.useState(false);
  const [detailsError, setDetailsError] = React.useState<string | null>(null);
  const autoCloseRef = React.useRef(autoClose);
  const isMountedRef = React.useRef(true);
  const { accountId, name, provider, region, vpcId } = loadBalancerParams;

  React.useEffect(() => {
    return () => {
      isMountedRef.current = false;
    };
  }, []);

  React.useEffect(() => {
    autoCloseRef.current = autoClose;
  }, [autoClose]);

  const loadDetails = React.useCallback(async () => {
    if (!loaded || error) {
      return;
    }

    setLoadingDetails(true);
    setDetailsError(null);
    try {
      const loadBalancer = await loadAmazonLoadBalancerDetails({
        app,
        autoClose: () => autoCloseRef.current(),
        loadBalancerParams: { accountId, name, provider, region, vpcId },
        loadBalancers,
        securityGroupReader,
      });
      if (isMountedRef.current) {
        setData(loadBalancer);
      }
    } catch (e) {
      if (isMountedRef.current) {
        setDetailsError(e?.message || String(e));
      }
    } finally {
      if (isMountedRef.current) {
        setLoadingDetails(false);
      }
    }
  }, [accountId, app, error, loadBalancers, loaded, name, provider, region, securityGroupReader, vpcId]);

  React.useEffect(() => {
    loadDetails();
  }, [loadDetails]);

  return {
    data,
    loading: !loaded || loadingDetails,
    error: error || detailsError,
    refetch: async () => refresh(),
  };
}

function sortServerGroups(a: any, b: any): number {
  if (a.isDisabled !== b.isDisabled) {
    return a.isDisabled ? 1 : -1;
  }
  return b.name.localeCompare(a.name);
}

function sortTargetGroups(a: any, b: any): number {
  if (a.isDisabled !== b.isDisabled) {
    return a.isDisabled ? 1 : -1;
  }
  return b.name.localeCompare(a.name);
}

export function AmazonLoadBalancerActions({ app, loadBalancer }: ILoadBalancerActionsProps): JSX.Element {
  const amazonLoadBalancer = loadBalancer as IAmazonLoadBalancer;
  const insightActions = (amazonLoadBalancer.elb as any)?.insightActions || [];
  return (
    <>
      <LoadBalancerActions app={app} loadBalancer={amazonLoadBalancer} />
      {insightActions.length > 0 && (
        <Dropdown className="dropdown" id="amazon-load-balancer-insights-dropdown">
          <Dropdown.Toggle className="btn btn-sm btn-default dropdown-toggle">Insight</Dropdown.Toggle>
          <Dropdown.Menu>
            {insightActions.map((action: any) => (
              <MenuItem key={action.url || action.label} href={action.url} target="_blank">
                {action.label}
              </MenuItem>
            ))}
          </Dropdown.Menu>
        </Dropdown>
      )}
    </>
  );
}

export function AmazonLoadBalancerDetailsSection({ app, loadBalancer }: ILoadBalancerDetailsSectionProps): JSX.Element {
  const amazonLoadBalancer = loadBalancer as IAmazonLoadBalancer;
  const elb = amazonLoadBalancer.elb || ({} as IAmazonLoadBalancerSourceData);
  const dnsName = elb.dnsname || (elb as any).dnsName;
  const subnetPurpose = head((amazonLoadBalancer.subnetDetails || []).map((subnet) => subnet.purpose)) || '';

  return (
    <>
      {amazonLoadBalancer.isManaged && (
        <ManagedResourceDetailsIndicator
          resourceSummary={amazonLoadBalancer.managedResourceSummary}
          application={app}
        />
      )}
      <CollapsibleSection heading="Load Balancer Details" defaultExpanded={true}>
        <dl className="dl-horizontal dl-narrow">
          <dt>Created</dt>
          <dd>{elb.createdTime ? timestamp(elb.createdTime) : '-'}</dd>
          <dt>In</dt>
          <dd>
            <AccountTag account={amazonLoadBalancer.account} /> {amazonLoadBalancer.region}
          </dd>
          <dt>VPC</dt>
          <dd>
            <VpcTag vpcId={elb.vpcId || amazonLoadBalancer.vpcId} />
          </dd>
          <dt>Subnet</dt>
          <dd>{subnetPurpose}</dd>
          <dt>Scheme</dt>
          <dd>{amazonLoadBalancer.scheme || '-'}</dd>
          {amazonLoadBalancer.loadBalancerType && <dt>Type</dt>}
          {amazonLoadBalancer.loadBalancerType && <dd>{amazonLoadBalancer.loadBalancerType}</dd>}
          {amazonLoadBalancer.ipAddressTypeDescription && <dt>IP Type</dt>}
          {amazonLoadBalancer.ipAddressTypeDescription && <dd>{amazonLoadBalancer.ipAddressTypeDescription}</dd>}
        </dl>
        {!!elb.availabilityZones?.length && (
          <dl className="horizontal-when-filters-collapsed">
            <dt>Availability Zones</dt>
            <dd>
              <ul className="collapse-margin-on-filter-collapse">
                {elb.availabilityZones.map((availabilityZone) => (
                  <li key={availabilityZone}>{availabilityZone}</li>
                ))}
              </ul>
            </dd>
          </dl>
        )}
        {!!amazonLoadBalancer.serverGroups?.length && (
          <dl className="horizontal-when-filters-collapsed">
            <dt>Server Groups</dt>
            <dd>
              <ul className="collapse-margin-on-filter-collapse">
                {amazonLoadBalancer.serverGroups
                  .slice()
                  .sort(sortServerGroups)
                  .map((serverGroup: any) => (
                    <li key={serverGroup.name}>
                      <UISref
                        to="^.serverGroup"
                        params={{
                          region: serverGroup.region,
                          accountId: serverGroup.account,
                          serverGroup: serverGroup.name,
                          provider: 'aws',
                        }}
                      >
                        <a>{serverGroup.name}</a>
                      </UISref>
                    </li>
                  ))}
              </ul>
            </dd>
          </dl>
        )}
        {!!(amazonLoadBalancer as IAmazonApplicationLoadBalancer).targetGroups?.length && (
          <dl className="horizontal-when-filters-collapsed">
            <dt>Target Groups</dt>
            <dd>
              <ul className="collapse-margin-on-filter-collapse">
                {(amazonLoadBalancer as IAmazonApplicationLoadBalancer).targetGroups
                  .slice()
                  .sort(sortTargetGroups)
                  .map((targetGroup) => (
                    <li key={targetGroup.name}>
                      <UISref
                        to="^.targetGroupDetails"
                        params={{
                          region: targetGroup.region,
                          loadBalancerName: amazonLoadBalancer.name,
                          accountId: targetGroup.account,
                          name: targetGroup.name,
                          vpcId: targetGroup.vpcId,
                          provider: 'aws',
                        }}
                      >
                        <a>{targetGroup.name}</a>
                      </UISref>
                    </li>
                  ))}
              </ul>
            </dd>
          </dl>
        )}
        {dnsName && (
          <dl className="horizontal-when-filters-collapsed">
            <dt>DNS Name</dt>
            <dd>
              <a target="_blank" href={`${amazonLoadBalancer.elbProtocol || 'http:'}//${dnsName}`}>
                {dnsName}
              </a>{' '}
              <CopyToClipboard text={dnsName} toolTip="Copy DNS Name to clipboard" />
            </dd>
          </dl>
        )}
      </CollapsibleSection>
    </>
  );
}

export function AmazonLoadBalancerStatusSection({ loadBalancer }: ILoadBalancerDetailsSectionProps): JSX.Element {
  return (
    <CollapsibleSection heading="Status" defaultExpanded={loadBalancer.loadBalancerType === 'classic'}>
      {loadBalancer.loadBalancerType === 'classic' ? (
        <HealthCounts className="pull-left" container={loadBalancer.instanceCounts} />
      ) : (
        <span>Select a target group to check the instance health status from the view of the target group.</span>
      )}
    </CollapsibleSection>
  );
}

function renderListenerAction(action: IActionDetails, loadBalancer: IAmazonLoadBalancer): JSX.Element {
  if (action.type === 'redirect') {
    const redirectConfig = action.redirectConfig;
    return (
      <span>
        {redirectConfig.protocol !== '#{protocol}' && `${redirectConfig.protocol}:`}
        {redirectConfig.host !== '#{host}' && ` ${redirectConfig.host} `}
        {redirectConfig.port !== '#{port}' && ` ${redirectConfig.port} `}
        {redirectConfig.path !== '/#{path}' && ` ${redirectConfig.path} `}
        {redirectConfig.query !== '#{query}' && ` ?${redirectConfig.query} `}({redirectConfig.statusCode})
      </span>
    );
  }

  if (action.type === 'authenticate-oidc') {
    const clientId = action.authenticateOidcConfig?.clientId;
    return (
      <span>
        <i className="fas fa-fw fa-user-lock" /> {clientId}
      </span>
    );
  }

  if (action.targetGroupName && action.targetGroup) {
    return (
      <span>
        <i className="fa fa-fw fa-crosshairs icon" aria-hidden="true" />
        <UISref
          to="^.targetGroupDetails"
          params={{
            region: action.targetGroup.region,
            loadBalancerName: loadBalancer.name,
            accountId: action.targetGroup.account,
            name: action.targetGroup.name,
            vpcId: action.targetGroup.vpcId,
            provider: 'aws',
          }}
        >
          <a>{action.targetGroupName}</a>
        </UISref>
      </span>
    );
  }

  return (
    <span>
      <i className="fa fa-fw fa-crosshairs icon" aria-hidden="true" /> {action.targetGroupName || action.type}
    </span>
  );
}

export function AmazonLoadBalancerListenersSection({ loadBalancer }: ILoadBalancerDetailsSectionProps): JSX.Element {
  const amazonLoadBalancer = loadBalancer as IAmazonLoadBalancer;
  const elb = amazonLoadBalancer.elb as IClassicLoadBalancerSourceData;

  if (amazonLoadBalancer.loadBalancerType === 'classic') {
    return (
      <CollapsibleSection heading="Listeners">
        <dl>
          <dt>Load Balancer -&gt; Instance</dt>
          {(elb.listenerDescriptions || []).map((description: any, index: number) => (
            <dd key={index}>
              {description.listener.protocol}:{description.listener.loadBalancerPort} -&gt;{' '}
              {description.listener.instanceProtocol}:{description.listener.instancePort}
            </dd>
          ))}
        </dl>
      </CollapsibleSection>
    );
  }

  return (
    <CollapsibleSection heading="Listeners">
      {(amazonLoadBalancer.listenerRows || []).map((listener: any) => (
        <div key={listener.in}>
          <div className="listener-targets">{listener.in} -&gt;</div>
          <div className="listener-targets">
            {listener.actions.map((action: IActionDetails, index: number) => (
              <div key={`${listener.in}-${index}`}>{renderListenerAction(action, amazonLoadBalancer)}</div>
            ))}
          </div>
        </div>
      ))}
    </CollapsibleSection>
  );
}

export function AmazonLoadBalancerFirewallsSection({ loadBalancer }: ILoadBalancerDetailsSectionProps): JSX.Element {
  const amazonLoadBalancer = loadBalancer as IAmazonLoadBalancer;
  const securityGroups = sortBy((amazonLoadBalancer.securityGroups || []) as any[], 'name');
  if (amazonLoadBalancer.loadBalancerType === 'network' && !securityGroups.length) {
    return null;
  }

  return (
    <CollapsibleSection heading={FirewallLabels.get('Firewalls')}>
      <ul>
        {securityGroups.map((securityGroup) => (
          <li key={securityGroup.id || securityGroup.name}>
            <UISref
              to="^.firewallDetails"
              params={{
                name: securityGroup.name,
                accountId: amazonLoadBalancer.account,
                region: amazonLoadBalancer.region,
                vpcId: amazonLoadBalancer.vpcId,
                provider: amazonLoadBalancer.provider,
              }}
            >
              <a>
                {securityGroup.name} ({securityGroup.id})
              </a>
            </UISref>
          </li>
        ))}
      </ul>
    </CollapsibleSection>
  );
}

export function AmazonLoadBalancerSubnetsSection({ loadBalancer }: ILoadBalancerDetailsSectionProps): JSX.Element {
  const subnetDetails = ((loadBalancer as IAmazonLoadBalancer).subnetDetails || []).filter(Boolean);
  return (
    <CollapsibleSection heading="Subnets">
      {!subnetDetails.length && <h5>No subnets</h5>}
      {subnetDetails.map((subnet: any) => (
        <div key={subnet.id} className="bottom-border">
          <h5>
            <strong>{subnet.id}</strong>
          </h5>
          <dl className="dl-horizontal dl-narrow">
            <dt>Purpose</dt>
            <dd>{subnet.purpose}</dd>
            <dt>State</dt>
            <dd>{subnet.state}</dd>
            <dt>Cidr Block</dt>
            <dd>{subnet.cidrBlock}</dd>
          </dl>
        </div>
      ))}
    </CollapsibleSection>
  );
}

export function AmazonLoadBalancerHealthChecksSection({
  loadBalancer,
}: ILoadBalancerDetailsSectionProps): JSX.Element | null {
  if (loadBalancer.loadBalancerType !== 'classic') {
    return null;
  }
  const healthCheck = ((loadBalancer as IAmazonLoadBalancer).elb as IClassicLoadBalancerSourceData)?.healthCheck;
  return (
    <CollapsibleSection heading="Health Checks">
      <dl className="horizontal-when-filters-collapsed">
        <dt>Target</dt>
        <dd>{healthCheck?.target || '-'}</dd>
        <dt>Timeout</dt>
        <dd>{healthCheck?.timeout} seconds</dd>
        <dt>Interval</dt>
        <dd>{healthCheck?.interval} seconds</dd>
        <dt>Healthy Threshold</dt>
        <dd>{healthCheck?.healthyThreshold}</dd>
        <dt>Unhealthy Threshold</dt>
        <dd>{healthCheck?.unhealthyThreshold}</dd>
      </dl>
    </CollapsibleSection>
  );
}

export const amazonLoadBalancerDetailsSections = [
  AmazonLoadBalancerDetailsSection,
  AmazonLoadBalancerStatusSection,
  AmazonLoadBalancerListenersSection,
  AmazonLoadBalancerFirewallsSection,
  AmazonLoadBalancerSubnetsSection,
  AmazonLoadBalancerHealthChecksSection,
];

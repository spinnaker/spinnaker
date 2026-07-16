import { UISref } from '@uirouter/react';
import React from 'react';
import { Dropdown, MenuItem } from 'react-bootstrap';

import type {
  Application,
  ILoadBalancer,
  ILoadBalancerActionsProps,
  ILoadBalancerDeleteCommand,
  ILoadBalancerDetailsSectionProps,
  IUseDetailsHookProps,
  LoadBalancerReader as LoadBalancerReaderType,
  SecurityGroupReader,
  UseDetailsResult,
} from '@spinnaker/core';
import {
  AccountTag,
  CollapsibleSection,
  ConfirmationModalService,
  CopyToClipboard,
  FirewallLabels,
  HealthCounts,
  LoadBalancerReader,
  LoadBalancerWriter,
  timestamp,
  useDataSource,
} from '@spinnaker/core';

import { AzureLoadBalancerModal } from '../configure/AzureLoadBalancerModal';

interface ILoadAzureLoadBalancerDetailsProps {
  app: Application;
  autoClose: () => void;
  loadBalancers?: ILoadBalancer[];
  loadBalancerParams: IAzureLoadBalancerStateParams;
  loadBalancerReader: LoadBalancerReaderType;
  securityGroupReader?: SecurityGroupReader;
}

interface IAzureLoadBalancerStateParams {
  accountId: string;
  name: string;
  provider: string;
  region: string;
}

const loadBalancerReader = new LoadBalancerReader(null, null);

function formatLoadBalancerType(loadBalancerType: string): string {
  if (!loadBalancerType?.includes('_')) {
    return loadBalancerType;
  }

  return loadBalancerType
    .split('_')
    .map((part) => {
      const lower = part.toLowerCase();
      return `${lower.substring(0, 1).toUpperCase()}${lower.substring(1)}`;
    })
    .join(' ');
}

function resolveSecurityGroups(
  app: Application,
  loadBalancer: ILoadBalancer,
  loadBalancerParams: IAzureLoadBalancerStateParams,
  securityGroupReader?: SecurityGroupReader,
): any[] {
  const securityGroupIds = loadBalancer.elb?.securityGroups || loadBalancer.securityGroups || [];
  if (!securityGroupIds.length) {
    return [];
  }

  return securityGroupIds
    .map((securityGroupId: string) => {
      if (securityGroupReader) {
        return securityGroupReader.getApplicationSecurityGroup(
          app,
          loadBalancerParams.accountId,
          loadBalancerParams.region,
          securityGroupId,
        );
      }
      return app['securityGroupsIndex']?.[loadBalancerParams.accountId]?.[loadBalancerParams.region]?.[securityGroupId];
    })
    .filter(Boolean)
    .sort((a: any, b: any) => a.name.localeCompare(b.name));
}

export async function loadAzureLoadBalancerDetails({
  app,
  autoClose,
  loadBalancers,
  loadBalancerParams,
  loadBalancerReader: reader,
  securityGroupReader,
}: ILoadAzureLoadBalancerDetailsProps): Promise<ILoadBalancer | undefined> {
  const { accountId, name, region } = loadBalancerParams;
  const loadBalancer = (loadBalancers || app.loadBalancers.data).find(
    (candidate: ILoadBalancer) =>
      candidate.name === name && candidate.region === region && candidate.account === accountId,
  );

  if (!loadBalancer) {
    autoClose();
    return undefined;
  }

  const details = await reader.getLoadBalancerDetails(
    loadBalancer.provider || loadBalancer.cloudProvider,
    accountId,
    region,
    name,
  );
  const elb = details.find((candidate: ILoadBalancer) => candidate.name === name);

  if (elb) {
    loadBalancer.elb = elb;
    loadBalancer.account = accountId;
    loadBalancer.securityGroups = resolveSecurityGroups(
      app,
      loadBalancer,
      loadBalancerParams,
      securityGroupReader,
    ) as any;
    loadBalancer.loadBalancerType = formatLoadBalancerType(loadBalancer.loadBalancerType);
  }

  return loadBalancer;
}

export function useAzureLoadBalancerDetails({
  app,
  loadBalancerParams,
  autoClose,
}: IUseDetailsHookProps): UseDetailsResult<ILoadBalancer> {
  const azureLoadBalancerParams = loadBalancerParams as IAzureLoadBalancerStateParams;
  const dataSource = app.getDataSource('loadBalancers');
  const { data: loadBalancers, loaded, refresh, error } = useDataSource<ILoadBalancer[]>(dataSource);
  const [data, setData] = React.useState<ILoadBalancer>();
  const [loadingDetails, setLoadingDetails] = React.useState(false);
  const [detailsError, setDetailsError] = React.useState<string | null>(null);

  const loadDetails = React.useCallback(async () => {
    if (!loaded || error) {
      return;
    }

    setLoadingDetails(true);
    setDetailsError(null);
    try {
      const loadBalancer = await loadAzureLoadBalancerDetails({
        app,
        loadBalancerParams: azureLoadBalancerParams,
        loadBalancers,
        autoClose,
        loadBalancerReader,
      });
      setData(loadBalancer);
    } catch (e) {
      setDetailsError(e?.message || String(e));
    } finally {
      setLoadingDetails(false);
    }
  }, [app, autoClose, azureLoadBalancerParams, error, loadBalancers, loaded]);

  React.useEffect(() => {
    loadDetails();
  }, [loadDetails]);

  return {
    data,
    loading: !loaded || loadingDetails,
    error: error || detailsError,
    refetch: async () => {
      refresh();
    },
  };
}

export function AzureLoadBalancerActions({ app, loadBalancer }: ILoadBalancerActionsProps) {
  const canDeleteLoadBalancer = !loadBalancer.serverGroups?.length;
  const editLoadBalancer = () => {
    AzureLoadBalancerModal.show({
      app,
      loadBalancer: { ...loadBalancer },
      isNew: false,
      loadBalancerType: loadBalancer.loadBalancerType,
    });
  };
  const deleteLoadBalancer = () => {
    if (!canDeleteLoadBalancer) {
      return;
    }

    const command: ILoadBalancerDeleteCommand = {
      cloudProvider: 'azure',
      loadBalancerName: loadBalancer.name,
      loadBalancerType: loadBalancer.loadBalancerType,
      credentials: loadBalancer.account,
      region: loadBalancer.region,
      appName: app.name,
    } as any;

    ConfirmationModalService.confirm({
      header: `Really delete ${loadBalancer.name}?`,
      buttonText: `Delete ${loadBalancer.name}`,
      account: loadBalancer.account,
      taskMonitorConfig: {
        application: app,
        title: `Deleting ${loadBalancer.name}`,
      },
      submitMethod: () => LoadBalancerWriter.deleteLoadBalancer(command, app),
    });
  };

  return (
    <Dropdown className="dropdown" id="azure-load-balancer-actions-dropdown">
      <Dropdown.Toggle className="btn btn-sm btn-primary dropdown-toggle">Load Balancer Actions</Dropdown.Toggle>
      <Dropdown.Menu>
        <MenuItem onClick={editLoadBalancer}>Edit Load Balancer</MenuItem>
        <MenuItem
          disabled={!canDeleteLoadBalancer}
          onClick={deleteLoadBalancer}
          title={
            canDeleteLoadBalancer
              ? undefined
              : 'You must detach all server groups before you can delete this load balancer.'
          }
        >
          Delete Load Balancer
        </MenuItem>
      </Dropdown.Menu>
    </Dropdown>
  );
}

function sortServerGroups(a: any, b: any): number {
  if (a.isDisabled !== b.isDisabled) {
    return a.isDisabled ? 1 : -1;
  }
  return b.name.localeCompare(a.name);
}

export function AzureLoadBalancerDetailsSection({ loadBalancer }: ILoadBalancerDetailsSectionProps) {
  const elb = loadBalancer.elb || {};
  const serverGroups = loadBalancer.serverGroups || [];
  const showNetwork = loadBalancer.loadBalancerType === 'Azure Application Gateway';

  return (
    <CollapsibleSection heading="Load Balancer Details" defaultExpanded={true}>
      <dl className="dl-horizontal dl-narrow">
        <dt>Type</dt>
        <dd>{loadBalancer.loadBalancerType || '-'}</dd>
        <dt>Created</dt>
        <dd>{elb.createdTime ? timestamp(elb.createdTime) : '-'}</dd>
        <dt>In</dt>
        <dd>
          <AccountTag account={loadBalancer.account} /> {loadBalancer.region}
        </dd>
        {showNetwork && <dt>VNet</dt>}
        {showNetwork && <dd>{elb.vnet || '-'}</dd>}
        {showNetwork && <dt>Subnet</dt>}
        {showNetwork && <dd>{elb.subnet || '-'}</dd>}
      </dl>
      {!!serverGroups.length && (
        <dl className="horizontal-when-filters-collapsed">
          <dt>Server Groups</dt>
          <dd>
            <ul className="collapse-margin-on-filter-collapse">
              {serverGroups
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
                        provider: 'azure',
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
      {elb.dnsName && (
        <dl className="horizontal-when-filters-collapsed">
          <dt>DNS Name:</dt>
          <dd>
            <a target="_blank" href={`//${elb.dnsName}`}>
              {elb.dnsName}
            </a>{' '}
            <CopyToClipboard text={elb.dnsName} toolTip="Copy DNS Name to clipboard" />
          </dd>
        </dl>
      )}
    </CollapsibleSection>
  );
}

export function AzureLoadBalancerListenersSection({ loadBalancer }: ILoadBalancerDetailsSectionProps) {
  const rules = loadBalancer.elb?.loadBalancingRules || [];

  return (
    <CollapsibleSection heading="Listeners">
      <dl>
        <dt>Load Balancer -&gt; Instance</dt>
        {rules.length ? (
          rules.map((rule: any, index: number) => (
            <dd key={rule.ruleName || index}>
              {rule.protocol}:{rule.externalPort} -&gt; {rule.backendPort}
            </dd>
          ))
        ) : (
          <dd>-</dd>
        )}
      </dl>
    </CollapsibleSection>
  );
}

export function AzureLoadBalancerHealthChecksSection({ loadBalancer }: ILoadBalancerDetailsSectionProps) {
  const probe = loadBalancer.elb?.probes?.[0];

  return (
    <CollapsibleSection heading="Health Checks">
      <dl className="horizontal-when-filters-collapsed">
        <dt>Target</dt>
        <dd>{probe?.probeProtocol || '-'}</dd>
        <dt>Interval</dt>
        <dd>{probe?.probeInterval ? `${probe.probeInterval} seconds` : '-'}</dd>
        <dt>Unhealthy Threshold</dt>
        <dd>{probe?.unhealthyThreshold || '-'}</dd>
        <dt>Timeout</dt>
        <dd>{probe?.timeout ? `${probe.timeout} seconds` : '-'}</dd>
      </dl>
    </CollapsibleSection>
  );
}

export function AzureLoadBalancerStatusSection({ loadBalancer }: ILoadBalancerDetailsSectionProps) {
  return (
    <CollapsibleSection heading="Status" defaultExpanded={true}>
      <HealthCounts className="pull-left" container={loadBalancer.instanceCounts} />
    </CollapsibleSection>
  );
}

export function AzureLoadBalancerFirewallsSection({ loadBalancer }: ILoadBalancerDetailsSectionProps) {
  const securityGroups = (loadBalancer.securityGroups || []) as any[];

  return (
    <CollapsibleSection heading={FirewallLabels.get('Firewalls')}>
      <ul>
        {securityGroups.map((securityGroup) => (
          <li key={securityGroup.id || securityGroup.name}>
            <UISref
              to="^.firewallDetails"
              params={{
                name: securityGroup.name,
                accountId: loadBalancer.account,
                region: loadBalancer.region,
                vpcId: loadBalancer.vpcId,
                provider: loadBalancer.provider,
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

export const azureLoadBalancerDetailsSections = [
  AzureLoadBalancerDetailsSection,
  AzureLoadBalancerStatusSection,
  AzureLoadBalancerListenersSection,
  AzureLoadBalancerFirewallsSection,
  AzureLoadBalancerHealthChecksSection,
];

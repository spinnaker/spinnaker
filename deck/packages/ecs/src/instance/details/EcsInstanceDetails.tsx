import React from 'react';

import type { IAmazonHealth } from '@spinnaker/amazon';
import { InstanceInformation, InstanceStatus } from '@spinnaker/amazon';
import type { Application, IInstanceDetailsProps, IMoniker } from '@spinnaker/core';
import {
  AngularServices,
  CollapsibleSection,
  ConsoleOutputLink,
  CopyToClipboard,
  InstanceDetailsHeader,
  InstanceLinks,
  InstanceReader,
  LabeledValue,
  RecentHistoryService,
  SubnetTag,
} from '@spinnaker/core';

interface IEcsInstanceParams {
  account?: string;
  accountId?: string;
  instanceId: string;
  provider?: string;
  region?: string;
  [key: string]: any;
}

export interface IEcsInstanceDetailsProps extends Partial<IInstanceDetailsProps> {
  accountId?: string;
  app: Application;
  environment?: string;
  instance?: IEcsInstanceParams;
  moniker?: IMoniker;
  $stateParams?: IInstanceDetailsProps['$stateParams'] & Partial<IEcsInstanceParams>;
}

interface IEcsInstanceLookup {
  account?: string;
  loadBalancers?: string[];
  region?: string;
  serverGroup?: string;
  summary?: any;
  targetGroups?: string[];
  vpcId?: string;
}

interface IEcsLoadedInstance {
  [key: string]: any;
  account?: string;
  baseIpAddress?: string;
  healthMetrics?: IAmazonHealth[];
  instanceId?: string;
  region?: string;
}

interface IEcsInstanceDetailsState {
  instance?: IEcsLoadedInstance;
  instanceIdNotFound?: string;
  loading: boolean;
}

function matchesInstance(candidate: any, instanceId: string): boolean {
  return candidate === instanceId || candidate?.id === instanceId;
}

function matchesScope(candidate: any, instance: IEcsInstanceParams): boolean {
  return (
    (!instance.account || candidate.account === instance.account) &&
    (!instance.region || candidate.region === instance.region)
  );
}

function targetGroupNames(targetGroups: any): string[] {
  const groups = Array.isArray(targetGroups) ? targetGroups : targetGroups ? [targetGroups] : [];
  return groups
    .map((targetGroup) =>
      typeof targetGroup === 'string' ? targetGroup : targetGroup.targetGroupName || targetGroup.name,
    )
    .filter(Boolean);
}

function findInstance(app: Application, instance: IEcsInstanceParams): IEcsInstanceLookup | undefined {
  if (app.isStandalone || !app.serverGroups) {
    return {
      account: instance.account || instance.accountId,
      region: instance.region,
      summary: instance,
    };
  }

  for (const serverGroup of app.serverGroups.data || []) {
    if (!matchesScope(serverGroup, instance)) {
      continue;
    }
    const summary = (serverGroup.instances || []).find((candidate: any) =>
      matchesInstance(candidate, instance.instanceId),
    );
    if (summary) {
      return {
        account: serverGroup.account,
        loadBalancers: serverGroup.loadBalancers || [],
        region: serverGroup.region,
        serverGroup: serverGroup.name,
        summary,
        targetGroups: targetGroupNames(serverGroup.targetGroups || serverGroup.targetGroup),
        vpcId: serverGroup.vpcId,
      };
    }
  }

  for (const loadBalancer of app.loadBalancers?.data || []) {
    if (!matchesScope(loadBalancer, instance)) {
      continue;
    }
    const summary = (loadBalancer.instances || []).find((candidate: any) =>
      matchesInstance(candidate, instance.instanceId),
    );
    if (summary) {
      return {
        account: loadBalancer.account,
        loadBalancers: [loadBalancer.name],
        region: loadBalancer.region,
        summary,
        vpcId: loadBalancer.vpcId,
      };
    }

    for (const targetGroup of loadBalancer.targetGroups || loadBalancer.targetGroup || []) {
      if (!matchesScope({ account: loadBalancer.account, region: loadBalancer.region, ...targetGroup }, instance)) {
        continue;
      }
      const targetSummary = (targetGroup.instances || []).find((candidate: any) =>
        matchesInstance(candidate, instance.instanceId),
      );
      if (targetSummary) {
        return {
          account: targetGroup.account || loadBalancer.account,
          region: targetGroup.region || loadBalancer.region,
          summary: typeof targetSummary === 'string' ? { id: targetSummary } : targetSummary,
          targetGroups: targetGroupNames(targetGroup),
          vpcId: loadBalancer.vpcId,
        };
      }
    }

    for (const serverGroup of (loadBalancer.serverGroups || []).filter((candidate: any) => candidate.isDisabled)) {
      const disabledSummary = (serverGroup.instances || []).find((candidate: any) =>
        matchesInstance(candidate, instance.instanceId),
      );
      if (disabledSummary) {
        return {
          account: loadBalancer.account,
          loadBalancers: [loadBalancer.name],
          region: loadBalancer.region,
          serverGroup: serverGroup.name,
          summary: disabledSummary,
          vpcId: loadBalancer.vpcId,
        };
      }
    }
  }

  return undefined;
}

function addTargetGroupHealthDetails(
  healthMetrics: IAmazonHealth[],
  loadBalancers: any[],
  account: string,
  region: string,
): IAmazonHealth[] {
  const targetGroups = loadBalancers.flatMap((loadBalancer) =>
    (loadBalancer.targetGroups || loadBalancer.targetGroup || []).map((targetGroup: any) => ({
      account: loadBalancer.account,
      region: loadBalancer.region,
      ...targetGroup,
    })),
  );

  return healthMetrics.map((metric) => {
    if (metric.type !== 'TargetGroup' || !metric.targetGroups) {
      return metric;
    }

    return {
      ...metric,
      targetGroups: metric.targetGroups.map((health: any) => {
        const name = health.targetGroupName || health.name;
        const targetGroup = targetGroups.find(
          (candidate: any) =>
            name &&
            (candidate.targetGroupName || candidate.name) === name &&
            candidate.account === account &&
            candidate.region === region,
        );
        if (!targetGroup) {
          return health;
        }

        const port =
          targetGroup.healthCheckPort === 'traffic-port' || targetGroup.healthCheckPort == null
            ? targetGroup.port
            : targetGroup.healthCheckPort;
        return {
          ...health,
          ...(port == null ? {} : { healthCheckPath: `:${port}${targetGroup.healthCheckPath || ''}` }),
          ...(targetGroup.healthCheckProtocol
            ? { healthCheckProtocol: targetGroup.healthCheckProtocol.toLowerCase() }
            : {}),
        };
      }),
    } as IAmazonHealth;
  });
}

function extractHealthMetrics(
  app: Application,
  summary: any,
  details: any,
  account: string,
  region: string,
): IAmazonHealth[] {
  const summaryHealth = app.isStandalone ? details.health : summary.health;
  const healthMetrics = ((summaryHealth?.length ? summaryHealth : details.health) || [])
    .filter((metric: IAmazonHealth) => metric.type !== 'Ecs' || metric.state !== 'Unknown')
    .map((metric: IAmazonHealth) => {
      const latest = (details.health || []).find((candidate: IAmazonHealth) => candidate.type === metric.type);
      return latest ? { ...latest, ...metric } : { ...metric };
    });

  return app.isStandalone
    ? healthMetrics
    : addTargetGroupHealthDetails(healthMetrics, app.loadBalancers?.data || [], account, region);
}

async function loadInstance(app: Application, params: IEcsInstanceParams): Promise<IEcsLoadedInstance | undefined> {
  const lookup = findInstance(app, params);
  if (!lookup?.summary || !lookup.account || !lookup.region) {
    return undefined;
  }

  RecentHistoryService.addExtraDataToLatest('instances', {
    account: lookup.account,
    region: lookup.region,
    ...(lookup.serverGroup ? { serverGroup: lookup.serverGroup } : {}),
    ...(lookup.vpcId ? { vpcId: lookup.vpcId } : {}),
  });

  const details = (await InstanceReader.getInstanceDetails(lookup.account, lookup.region, params.instanceId)) as any;
  const instanceId = params.instanceId;
  const baseIpAddress =
    details.publicDnsName ||
    details.privateIpAddress ||
    details.networkInterface?.privateIpv4Address ||
    details.privateAddress ||
    details.networkInterface?.ipv6Address;

  return {
    ...lookup.summary,
    ...details,
    account: lookup.account,
    baseIpAddress,
    cloudProvider: details.cloudProvider || lookup.summary.cloudProvider || 'ecs',
    healthMetrics: extractHealthMetrics(app, lookup.summary, details, lookup.account, lookup.region),
    instanceId,
    loadBalancers: lookup.loadBalancers || details.loadBalancers || lookup.summary.loadBalancers,
    provider: details.provider || lookup.summary.provider || 'ecs',
    region: lookup.region,
    serverGroup: lookup.serverGroup || details.serverGroup || lookup.summary.serverGroup,
    targetGroups: lookup.targetGroups || details.targetGroups || lookup.summary.targetGroups,
    vpcId: lookup.vpcId || details.vpcId || lookup.summary.vpcId,
  };
}

function NetworkAddress({ address, label }: { address?: string; label: string }): JSX.Element | null {
  if (!address) {
    return null;
  }

  const hrefAddress = address.includes(':') ? `[${address}]` : address;
  return (
    <>
      <dt>{label}</dt>
      <dd>
        <a href={`http://${hrefAddress}`} target="_blank" rel="noopener noreferrer">
          {address}
        </a>
        <CopyToClipboard text={address} toolTip="Copy to clipboard" />
      </dd>
    </>
  );
}

export class EcsInstanceDetails extends React.Component<IEcsInstanceDetailsProps, IEcsInstanceDetailsState> {
  public state: IEcsInstanceDetailsState = {
    instance: undefined,
    instanceIdNotFound: undefined,
    loading: true,
  };

  private activeRequestId = 0;
  private dataReady = false;
  private isUnmounted = false;
  private unsubscribeFromRefresh?: () => void;

  public componentDidMount(): void {
    const { app } = this.props;
    const ready = app.isStandalone
      ? Promise.resolve()
      : Promise.all([app.serverGroups.ready(), app.loadBalancers.ready()]).then(() => undefined);

    ready.then(
      () => {
        if (this.isUnmounted) {
          return;
        }
        this.dataReady = true;
        this.retrieveInstance();
        if (!app.isStandalone && app.serverGroups?.onRefresh) {
          this.unsubscribeFromRefresh = app.serverGroups.onRefresh(null, () => this.retrieveInstance());
        }
      },
      () => {
        if (!this.isUnmounted) {
          this.closeDetails();
        }
      },
    );
  }

  public componentDidUpdate(previousProps: IEcsInstanceDetailsProps): void {
    const previous = this.getInstanceParams(previousProps);
    const current = this.getInstanceParams();
    if (
      this.dataReady &&
      (previous.account !== current.account ||
        previous.instanceId !== current.instanceId ||
        previous.region !== current.region)
    ) {
      this.retrieveInstance(true);
    }
  }

  public componentWillUnmount(): void {
    this.isUnmounted = true;
    this.activeRequestId += 1;
    this.unsubscribeFromRefresh?.();
  }

  private getInstanceParams(props: IEcsInstanceDetailsProps = this.props): IEcsInstanceParams {
    const params = props.instance || props.$stateParams || ({} as IEcsInstanceParams);
    return {
      ...params,
      account: params.account || params.accountId || props.accountId,
      instanceId: params.instanceId,
    };
  }

  private closeDetails(): void {
    AngularServices.$state.go('^', { allowModalToStayOpen: true }, { location: 'replace' });
  }

  private retrieveInstance(clearInstance = false): void {
    const requestId = ++this.activeRequestId;
    const params = this.getInstanceParams();
    this.setState(
      clearInstance
        ? { instance: undefined, instanceIdNotFound: undefined, loading: true }
        : { instanceIdNotFound: undefined, loading: true },
    );

    loadInstance(this.props.app, params).then(
      (instance) => {
        if (this.isUnmounted || requestId !== this.activeRequestId) {
          return;
        }
        this.setState({ instance, instanceIdNotFound: instance ? undefined : params.instanceId, loading: false });
      },
      () => {
        if (this.isUnmounted || requestId !== this.activeRequestId) {
          return;
        }
        if (this.props.app.isStandalone) {
          RecentHistoryService.removeLastItem('instances');
          this.setState({ instance: undefined, instanceIdNotFound: params.instanceId, loading: false });
        } else {
          this.closeDetails();
        }
      },
    );
  }

  public render(): JSX.Element {
    const { app, environment, moniker } = this.props;
    const { instance, instanceIdNotFound, loading } = this.state;
    const instanceId = instance?.instanceId || instanceIdNotFound || this.getInstanceParams().instanceId;

    return (
      <div className="details-panel">
        <div className="header">
          <InstanceDetailsHeader
            cloudProvider="ecs"
            healthState={instance?.healthState}
            instanceId={instanceId}
            loading={loading}
            standalone={app.isStandalone}
          />
        </div>
        {!loading && instance && (
          <div className="content" data-test-id="instanceDetails.content">
            <CollapsibleSection heading="Instance Information" defaultExpanded={true}>
              <dl className="dl-horizontal dl-narrow">
                <InstanceInformation
                  account={instance.account}
                  availabilityZone={instance.zone || instance.availabilityZone || instance.placement?.availabilityZone}
                  instanceType={instance.instanceType}
                  launchTime={instance.launchTime}
                  provider={instance.provider}
                  region={instance.region}
                  serverGroup={instance.serverGroup}
                />
                {instance.imageId && <LabeledValue label="Image ID" value={instance.imageId} />}
              </dl>
            </CollapsibleSection>
            <InstanceStatus
              healthMetrics={instance.healthMetrics || []}
              healthState={instance.healthState}
              metricTypes={['LoadBalancer', 'TargetGroup']}
              privateIpAddress={instance.privateIpAddress || instance.networkInterface?.privateIpv4Address}
            />
            <CollapsibleSection heading="Networking">
              <dl className="horizontal-when-filters-collapsed">
                {instance.vpcId && <LabeledValue label="VPC" value={instance.vpcId} />}
                {instance.subnetId && (
                  <LabeledValue label="Subnet" value={<SubnetTag subnetId={instance.subnetId} />} />
                )}
                <NetworkAddress address={instance.networkInterface?.privateIpv4Address} label="Private address (ENI)" />
                <NetworkAddress address={instance.networkInterface?.ipv6Address} label="IPv6 address (ENI)" />
                <NetworkAddress address={instance.privateAddress} label="Private address (Bridge)" />
              </dl>
            </CollapsibleSection>
            <CollapsibleSection heading="Console Output">
              <ConsoleOutputLink instance={instance as any} />
            </CollapsibleSection>
            <InstanceLinks
              address={instance.baseIpAddress}
              application={app}
              environment={environment}
              instance={instance as any}
              moniker={moniker}
            />
          </div>
        )}
        {!loading && !instance && instanceIdNotFound && (
          <div className="content">
            <div className="content-section">
              <div className="content-body text-center">
                <h3>Instance not found.</h3>
                <p>{instanceIdNotFound}</p>
              </div>
            </div>
          </div>
        )}
      </div>
    );
  }
}

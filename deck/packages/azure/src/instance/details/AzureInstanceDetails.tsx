import { UISref } from '@uirouter/react';
import React from 'react';
import { Dropdown, MenuItem } from 'react-bootstrap';

import type { Application, IInstanceDetailsProps, IRouterInjectedProps } from '@spinnaker/core';
import {
  AccountTag,
  CollapsibleSection,
  ConfirmationModalService,
  InstanceDetailsHeader,
  InstanceReader,
  InstanceWriter,
  RecentHistoryService,
  timestamp,
  withRouter,
} from '@spinnaker/core';

interface IAzureInstanceParams {
  account?: string;
  instanceId: string;
  provider?: string;
  region?: string;
}

interface IAzureInstanceDetailsProps extends Partial<IInstanceDetailsProps> {
  app: Application;
  initialInstance?: IAzureLoadedInstance;
  instance?: IAzureInstanceParams;
  $stateParams?: IInstanceDetailsProps['$stateParams'] & Partial<IAzureInstanceParams>;
}

interface IAzureLoadInstanceDetailsProps {
  app: Application;
  instance: IAzureInstanceParams;
}

interface IAzureInstanceLookupResult {
  account?: string;
  extraData?: { [key: string]: string };
  loadBalancers?: string[];
  region?: string;
  summary?: any;
  vpcId?: string;
}

export interface IAzureLoadedInstance {
  [key: string]: any;
  account?: string;
  baseIpAddress?: string;
  health?: any[];
  healthMetrics?: any[];
  instanceId?: string;
  instanceIdNotFound?: string;
  loadBalancers?: string[];
  region?: string;
  serverGroup?: string;
  vpcId?: string;
}

function findInServerGroups(app: Application, instanceId: string): IAzureInstanceLookupResult | undefined {
  const serverGroups = app.serverGroups?.data || [];
  for (const serverGroup of serverGroups) {
    const summary = serverGroup.instances?.find((possibleInstance: any) => possibleInstance.id === instanceId);
    if (summary) {
      return {
        account: serverGroup.account,
        extraData: { serverGroup: serverGroup.name, vpcId: serverGroup.vpcId },
        loadBalancers: serverGroup.loadBalancers,
        region: serverGroup.region,
        summary,
        vpcId: serverGroup.vpcId,
      };
    }
  }
  return undefined;
}

function findInLoadBalancerInstances(app: Application, instanceId: string): IAzureInstanceLookupResult | undefined {
  const loadBalancers = app.loadBalancers?.data || [];
  for (const loadBalancer of loadBalancers) {
    const summary = loadBalancer.instances?.find((possibleInstance: any) => possibleInstance.id === instanceId);
    if (summary) {
      return {
        account: loadBalancer.account,
        loadBalancers: [loadBalancer.name],
        region: loadBalancer.region,
        summary,
        vpcId: loadBalancer.vpcId,
      };
    }
  }
  return undefined;
}

function findInLoadBalancerServerGroups(app: Application, instanceId: string): IAzureInstanceLookupResult | undefined {
  const loadBalancers = app.loadBalancers?.data || [];
  for (const loadBalancer of loadBalancers) {
    const serverGroups = (loadBalancer.serverGroups || []).filter((serverGroup: any) => !serverGroup.isDisabled);
    for (const serverGroup of serverGroups) {
      const summary = serverGroup.instances?.find((possibleInstance: any) => possibleInstance.id === instanceId);
      if (summary) {
        return {
          account: loadBalancer.account,
          loadBalancers: [loadBalancer.name],
          region: loadBalancer.region,
          summary,
          vpcId: loadBalancer.vpcId,
        };
      }
    }
  }
  return undefined;
}

function findInDisabledLoadBalancerServerGroups(
  app: Application,
  instanceId: string,
): IAzureInstanceLookupResult | undefined {
  const loadBalancers = app.loadBalancers?.data || [];
  for (const loadBalancer of loadBalancers) {
    const disabledServerGroups = (loadBalancer.serverGroups || []).filter((serverGroup: any) => serverGroup.isDisabled);
    for (const serverGroup of disabledServerGroups) {
      const summary = serverGroup.instances?.find((possibleInstance: any) => possibleInstance.id === instanceId);
      if (summary) {
        return {
          account: loadBalancer.account,
          loadBalancers: [loadBalancer.name],
          region: loadBalancer.region,
          summary,
          vpcId: loadBalancer.vpcId,
        };
      }
    }
  }
  return undefined;
}

function findAzureInstanceSummary(
  app: Application,
  instance: IAzureInstanceParams,
): IAzureInstanceLookupResult | undefined {
  if (!app.serverGroups) {
    return { account: instance.account, loadBalancers: [], region: instance.region, summary: {} };
  }

  return (
    findInServerGroups(app, instance.instanceId) ||
    findInLoadBalancerInstances(app, instance.instanceId) ||
    findInLoadBalancerServerGroups(app, instance.instanceId) ||
    findInDisabledLoadBalancerServerGroups(app, instance.instanceId)
  );
}

function extractHealthMetrics(summary: any, details: any, standalone: boolean): any[] {
  const summaryHealth = standalone ? details.health : summary.health;
  const displayableMetrics = (summaryHealth || []).filter(
    (metric: any) => metric.type !== 'Azure' || metric.state !== 'Unknown',
  );
  const latestHealth = details.health || [];

  return displayableMetrics.map((metric: any) => {
    const latestMatch = latestHealth.find((latest: any) => latest.type === metric.type);
    return latestMatch ? { ...latestMatch, ...metric } : metric;
  });
}

export async function loadAzureInstanceDetails({
  app,
  instance,
}: IAzureLoadInstanceDetailsProps): Promise<IAzureLoadedInstance> {
  const lookup = findAzureInstanceSummary(app, instance);

  if (!lookup?.summary) {
    return { instanceIdNotFound: instance.instanceId };
  }

  const { account, region } = lookup;
  if (!account || !region) {
    return { instanceIdNotFound: instance.instanceId };
  }

  RecentHistoryService.addExtraDataToLatest('instances', {
    ...lookup.extraData,
    account,
    region,
  });

  const details = (await InstanceReader.getInstanceDetails(account, region, instance.instanceId)) as any;
  const healthMetrics = extractHealthMetrics(lookup.summary, details, app.isStandalone);
  const discoveryMetric = healthMetrics.find((metric: any) => metric.type === 'Discovery');
  const loadedInstance = {
    ...lookup.summary,
    ...details,
    account,
    baseIpAddress: details.publicDnsName || details.privateIpAddress,
    healthMetrics,
    instanceId: details.instanceId || lookup.summary.instanceId || lookup.summary.id || instance.instanceId,
    loadBalancers: lookup.loadBalancers,
    region,
    vpcId: lookup.vpcId,
  } as IAzureLoadedInstance;

  if (lookup.extraData?.serverGroup) {
    loadedInstance.serverGroup = lookup.extraData.serverGroup;
  }

  if (discoveryMetric?.vipAddress) {
    loadedInstance.vipAddress = discoveryMetric.vipAddress.includes(',')
      ? discoveryMetric.vipAddress.split(',')
      : [discoveryMetric.vipAddress];
  }

  return loadedInstance;
}

export class AzureInstanceDetailsComponent extends React.Component<
  IAzureInstanceDetailsProps & IRouterInjectedProps,
  { instance?: IAzureLoadedInstance; loading: boolean }
> {
  public state = { instance: this.props.initialInstance, loading: !this.props.initialInstance };

  private activeRequestId = 0;
  private isUnmounted = false;
  private unsubscribeFromRefresh: () => void;

  public componentDidMount(): void {
    if (this.props.initialInstance) {
      return;
    }

    const { app } = this.props;
    const retrieveWhenReady =
      app.isStandalone || !app.serverGroups
        ? Promise.resolve()
        : Promise.all([app.serverGroups.ready(), app.loadBalancers.ready()]);
    retrieveWhenReady.then(() => {
      if (this.isUnmounted) {
        return;
      }
      this.retrieveInstance();
      if (!app.isStandalone && app.serverGroups?.onRefresh) {
        this.unsubscribeFromRefresh = app.serverGroups.onRefresh(null, this.retrieveInstance);
      }
    });
  }

  public componentDidUpdate(prevProps: IAzureInstanceDetailsProps): void {
    const previousParams = this.getInstanceParams(prevProps);
    const currentParams = this.getInstanceParams();
    if (
      previousParams.account !== currentParams.account ||
      previousParams.instanceId !== currentParams.instanceId ||
      previousParams.region !== currentParams.region
    ) {
      this.retrieveInstance(true);
    }
  }

  public componentWillUnmount(): void {
    this.isUnmounted = true;
    if (this.unsubscribeFromRefresh) {
      this.unsubscribeFromRefresh();
    }
  }

  private getInstanceParams(
    props: IAzureInstanceDetailsProps & Partial<IRouterInjectedProps> = this.props,
  ): IAzureInstanceParams {
    const params = props.instance || props.$stateParams || props.stateParams;
    return {
      account: params?.account,
      instanceId: params?.instanceId,
      provider: params?.provider,
      region: params?.region,
    };
  }

  private retrieveInstance = (clearInstance = false): void => {
    const requestId = ++this.activeRequestId;
    const instanceParams = this.getInstanceParams();
    this.setState(clearInstance ? { instance: undefined, loading: true } : { loading: true });
    loadAzureInstanceDetails({ app: this.props.app, instance: instanceParams }).then(
      (instance) => {
        if (!this.isUnmounted && requestId === this.activeRequestId) {
          this.setState({ instance, loading: false });
        }
      },
      () => {
        if (!this.isUnmounted && requestId === this.activeRequestId) {
          this.setState({ loading: false });
          this.props.stateService.go('^');
        }
      },
    );
  };

  public render(): JSX.Element {
    const { app } = this.props;
    const { instance, loading } = this.state;
    const instanceId = instance?.instanceId || instance?.instanceIdNotFound || this.getInstanceParams().instanceId;

    return (
      <div className="details-panel">
        <div className="header">
          <InstanceDetailsHeader
            healthState={instance?.healthState}
            instanceId={instanceId}
            loading={loading}
            standalone={app.isStandalone}
          />
          {!loading && instance && !instance.instanceIdNotFound && (
            <div className="actions">
              <AzureInstanceActionsComponent
                app={app}
                instance={instance}
                router={this.props.router}
                stateParams={this.props.stateParams}
                stateService={this.props.stateService}
              />
            </div>
          )}
        </div>
        {!loading && instance && !instance.instanceIdNotFound && (
          <div className="content">
            <AzureInstanceInformationSection instance={instance} />
          </div>
        )}
        {!loading && instance?.instanceIdNotFound && (
          <div className="content">
            <div className="content-section">
              <div className="content-body text-center">
                <h3>Instance not found.</h3>
              </div>
            </div>
          </div>
        )}
      </div>
    );
  }
}

function hasHealthState(instance: IAzureLoadedInstance, healthProviderType: string, state: string): boolean {
  return (instance.health || []).some((health: any) => health.type === healthProviderType && health.state === state);
}

function canRegisterWithLoadBalancer(instance: IAzureLoadedInstance): boolean {
  if (!instance.loadBalancers?.length) {
    return false;
  }
  return (
    hasHealthState(instance, 'LoadBalancer', 'OutOfService') ||
    !(instance.health || []).some((health: any) => health.type === 'LoadBalancer')
  );
}

function canRegisterWithDiscovery(instance: IAzureLoadedInstance): boolean {
  return hasHealthState(instance, 'Discovery', 'OutOfService');
}

export function AzureInstanceActionsComponent({
  app,
  instance,
  stateService,
}: {
  app: Application;
  instance: IAzureLoadedInstance;
} & IRouterInjectedProps): JSX.Element {
  const loadBalancerNames = (instance.loadBalancers || []).join(' and ');
  const closeIfCurrentInstance = () => {
    if (stateService.includes('**.instanceDetails', { instanceId: instance.instanceId })) {
      stateService.go('^');
    }
  };
  const confirm = (
    header: string,
    buttonText: string,
    title: string,
    submitMethod: () => PromiseLike<any>,
    closeOnComplete = false,
  ) => {
    ConfirmationModalService.confirm({
      header,
      buttonText,
      account: instance.account,
      taskMonitorConfig: {
        application: app,
        title,
        onTaskComplete: closeOnComplete ? closeIfCurrentInstance : undefined,
      },
      submitMethod,
    });
  };

  return (
    <Dropdown className="dropdown" id="azure-instance-actions-dropdown">
      <Dropdown.Toggle className="btn btn-sm btn-primary dropdown-toggle">Instance Actions</Dropdown.Toggle>
      <Dropdown.Menu>
        <MenuItem
          onClick={() =>
            confirm(
              `Really terminate ${instance.instanceId}?`,
              `Terminate ${instance.instanceId}`,
              `Terminating ${instance.instanceId}`,
              () => InstanceWriter.terminateInstance(instance as any, app),
              true,
            )
          }
        >
          Terminate
        </MenuItem>
        {instance.serverGroup && (
          <MenuItem
            onClick={() =>
              confirm(
                `Really terminate ${instance.instanceId} and shrink ${instance.serverGroup}?`,
                `Terminate ${instance.instanceId} and shrink ${instance.serverGroup}`,
                `Terminating ${instance.instanceId} and shrinking server group`,
                () => InstanceWriter.terminateInstanceAndShrinkServerGroup(instance as any, app),
                true,
              )
            }
          >
            Terminate and Shrink Server Group
          </MenuItem>
        )}
        <MenuItem
          onClick={() =>
            confirm(
              `Really reboot ${instance.instanceId}?`,
              `Reboot ${instance.instanceId}`,
              `Rebooting ${instance.instanceId}`,
              () => InstanceWriter.rebootInstance(instance as any, app),
            )
          }
        >
          Reboot
        </MenuItem>
        {canRegisterWithLoadBalancer(instance) && (
          <MenuItem
            onClick={() =>
              confirm(
                `Really register ${instance.instanceId} with ${loadBalancerNames}?`,
                `Register ${instance.instanceId}`,
                `Registering ${instance.instanceId} with ${loadBalancerNames}`,
                () => InstanceWriter.registerInstanceWithLoadBalancer(instance as any, app),
              )
            }
          >
            Register with Load Balancer
          </MenuItem>
        )}
        {(instance.health || []).some((health: any) => health.type === 'LoadBalancer') && (
          <MenuItem
            onClick={() =>
              confirm(
                `Really deregister ${instance.instanceId} from ${loadBalancerNames}?`,
                `Deregister ${instance.instanceId}`,
                `Deregistering ${instance.instanceId} from ${loadBalancerNames}`,
                () => InstanceWriter.deregisterInstanceFromLoadBalancer(instance as any, app),
              )
            }
          >
            Deregister from Load Balancer
          </MenuItem>
        )}
        {canRegisterWithDiscovery(instance) && (
          <MenuItem
            onClick={() =>
              confirm(
                `Really enable ${instance.instanceId} in discovery?`,
                `Enable ${instance.instanceId}`,
                `Enabling ${instance.instanceId} in discovery`,
                () => InstanceWriter.enableInstanceInDiscovery(instance as any, app),
              )
            }
          >
            Enable in Discovery
          </MenuItem>
        )}
        {(instance.health || []).some((health: any) => health.type === 'Discovery') && (
          <MenuItem
            onClick={() =>
              confirm(
                `Really disable ${instance.instanceId} in discovery?`,
                `Disable ${instance.instanceId}`,
                `Disabling ${instance.instanceId} in discovery`,
                () => InstanceWriter.disableInstanceInDiscovery(instance as any, app),
              )
            }
          >
            Disable in Discovery
          </MenuItem>
        )}
      </Dropdown.Menu>
    </Dropdown>
  );
}

export const AzureInstanceActions = withRouter(AzureInstanceActionsComponent);
export const AzureInstanceDetails = withRouter(AzureInstanceDetailsComponent);

export function AzureInstanceInformationSection({ instance }: { instance: IAzureLoadedInstance }): JSX.Element {
  return (
    <CollapsibleSection heading="Instance Information" defaultExpanded={true}>
      <dl className="dl-horizontal dl-narrow">
        <dt>Launched</dt>
        <dd>{instance.launchTime ? timestamp(instance.launchTime) : '(Unknown)'}</dd>
        <dt>In</dt>
        <dd>
          <AccountTag account={instance.account} /> {instance.region || '(Unknown)'}
        </dd>
        <dt>Type</dt>
        <dd>{instance.instanceType || '(Unknown)'}</dd>
        {instance.serverGroup && <dt>Server Group</dt>}
        {instance.serverGroup && (
          <dd>
            <UISref
              to="^.serverGroup"
              params={{
                region: instance.region,
                accountId: instance.account,
                serverGroup: instance.serverGroup,
                provider: instance.provider,
              }}
            >
              <a>{instance.serverGroup}</a>
            </UISref>
          </dd>
        )}
      </dl>
    </CollapsibleSection>
  );
}

import { flatMap } from 'lodash';
import React from 'react';

import type { Application, IInstanceDetailsProps } from '@spinnaker/core';
import {
  CollapsibleSection,
  ConfirmationModalService,
  ConsoleOutputLink,
  InstanceActions,
  InstanceDetailsHeader,
  InstanceInsights,
  InstanceLinks,
  InstanceReader,
  ReactInjector,
  RecentHistoryService,
  SETTINGS,
} from '@spinnaker/core';

import { AmazonInstanceInformation } from './AmazonInstanceInformation';
import { InstanceDns } from './InstanceDns';
import { InstanceSecurityGroups } from './InstanceSecurityGroups';
import { InstanceStatus } from './InstanceStatus';
import { InstanceTags } from './InstanceTags';
import { AmazonInstanceWriter } from '../amazon.instance.write.service';
import { AWSProviderSettings } from '../../aws.settings';
import type { IAmazonHealth, IAmazonInstance } from '../../domain';
import { applyHealthCheckInfoToTargetGroups, getAllTargetGroups } from './utils';

interface IAmazonInstanceParams {
  account?: string;
  instanceId: string;
  provider?: string;
  region?: string;
}

interface IAmazonInstanceDetailsProps extends Partial<IInstanceDetailsProps> {
  accountId?: string;
  app: Application;
  environment?: string;
  initialInstance?: IAmazonLoadedInstance;
  instance?: IAmazonInstanceParams;
  $stateParams?: IInstanceDetailsProps['$stateParams'] & Partial<IAmazonInstanceParams>;
}

interface IAmazonInstanceLookupResult {
  account?: string;
  extraData?: { [key: string]: string };
  loadBalancers?: string[];
  region?: string;
  serverGroupDisabled?: boolean;
  summary?: any;
  targetGroups?: string[];
  vpcId?: string;
}

export interface IAmazonLoadedInstance extends IAmazonInstance {
  [key: string]: any;
  baseIpAddress?: string;
  healthMetrics?: IAmazonHealth[];
  instanceId?: string;
  instanceIdNotFound?: string;
  ipv6Addresses?: Array<{ ip: string; url: string }>;
  permanentIps?: string[];
  serverGroupDisabled?: boolean;
}

function findInServerGroups(app: Application, instanceId: string): IAmazonInstanceLookupResult | undefined {
  const serverGroups = app.serverGroups?.data || [];
  for (const serverGroup of serverGroups) {
    const summary = serverGroup.instances?.find((possibleInstance: any) => possibleInstance.id === instanceId);
    if (summary) {
      return {
        account: serverGroup.account,
        extraData: { serverGroup: serverGroup.name, vpcId: serverGroup.vpcId },
        loadBalancers: serverGroup.loadBalancers,
        region: serverGroup.region,
        serverGroupDisabled: serverGroup.isDisabled,
        summary,
        targetGroups: serverGroup.targetGroups,
        vpcId: serverGroup.vpcId,
      };
    }
  }
  return undefined;
}

function findInLoadBalancers(app: Application, instanceId: string): IAmazonInstanceLookupResult | undefined {
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

    const targetGroup = loadBalancer.targetGroups?.find((candidate: any) =>
      candidate.instances?.some((possibleInstance: any) => possibleInstance.id === instanceId),
    );
    if (targetGroup) {
      const targetInstance = targetGroup.instances.find((possibleInstance: any) => possibleInstance.id === instanceId);
      return {
        account: loadBalancer.account,
        region: loadBalancer.region,
        summary: targetInstance,
        targetGroups: [targetGroup.name],
        vpcId: loadBalancer.vpcId,
      };
    }
  }
  return undefined;
}

function findInDisabledLoadBalancerServerGroups(
  app: Application,
  instanceId: string,
): IAmazonInstanceLookupResult | undefined {
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

    const targetGroup = loadBalancer.targetGroups?.find((candidate: any) =>
      (candidate.serverGroups || []).some(
        (serverGroup: any) =>
          serverGroup.isDisabled &&
          serverGroup.instances?.some((possibleInstance: any) => possibleInstance.id === instanceId),
      ),
    );
    if (targetGroup) {
      const serverGroup = targetGroup.serverGroups.find((candidate: any) =>
        candidate.instances?.some((possibleInstance: any) => possibleInstance.id === instanceId),
      );
      const summary = serverGroup.instances.find((possibleInstance: any) => possibleInstance.id === instanceId);
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

function findAmazonInstanceSummary(
  app: Application,
  instance: IAmazonInstanceParams,
): IAmazonInstanceLookupResult | undefined {
  if (!app.serverGroups) {
    return {
      account: instance.account,
      loadBalancers: [],
      region: instance.region,
      summary: { id: instance.instanceId },
    };
  }

  return (
    findInServerGroups(app, instance.instanceId) ||
    findInLoadBalancers(app, instance.instanceId) ||
    findInDisabledLoadBalancerServerGroups(app, instance.instanceId)
  );
}

function extractHealthMetrics(app: Application, summary: any, details: any, account: string): IAmazonHealth[] {
  if (app.isStandalone) {
    summary.health = details.health;
  }

  const displayableMetrics = ((summary.health || []) as IAmazonHealth[]).filter(
    (metric) => metric.type !== 'Amazon' || metric.state !== 'Unknown',
  );

  if (!app.isStandalone) {
    const targetGroups = getAllTargetGroups(
      (app.loadBalancers?.data || []).filter((loadBalancer: any) => loadBalancer.cloudProvider === 'aws'),
    );
    applyHealthCheckInfoToTargetGroups(displayableMetrics, targetGroups, account);
  }

  (details.health || []).forEach((latest: IAmazonHealth) => {
    const metric = displayableMetrics.find((candidate) => candidate.type === latest.type);
    if (metric) {
      Object.assign(metric, { ...latest, ...metric });
    }
  });

  return displayableMetrics;
}

export async function loadAmazonInstanceDetails({
  app,
  instance,
}: {
  app: Application;
  instance: IAmazonInstanceParams;
}): Promise<IAmazonLoadedInstance> {
  const lookup = findAmazonInstanceSummary(app, instance);

  if (!lookup?.summary || !lookup.account || !lookup.region) {
    return ({ instanceIdNotFound: instance.instanceId } as unknown) as IAmazonLoadedInstance;
  }

  RecentHistoryService.addExtraDataToLatest('instances', {
    ...lookup.extraData,
    account: lookup.account,
    region: lookup.region,
  });

  const details = (await InstanceReader.getInstanceDetails(lookup.account, lookup.region, instance.instanceId)) as any;
  const loadedInstance = {
    ...lookup.summary,
    ...details,
    account: lookup.account,
    baseIpAddress: details.publicDnsName || details.privateIpAddress,
    healthMetrics: extractHealthMetrics(app, lookup.summary, details, lookup.account),
    instanceId: details.instanceId || lookup.summary.instanceId || lookup.summary.id || instance.instanceId,
    loadBalancers: lookup.loadBalancers,
    region: lookup.region,
    serverGroupDisabled: lookup.serverGroupDisabled,
    targetGroups: lookup.targetGroups,
    vpcId: lookup.vpcId,
  } as IAmazonLoadedInstance;

  if (lookup.extraData?.serverGroup) {
    loadedInstance.serverGroup = lookup.extraData.serverGroup;
  }

  if (loadedInstance.networkInterfaces) {
    const instancePort = app.attributes?.instancePort || SETTINGS.defaultInstancePort || 80;
    loadedInstance.ipv6Addresses = flatMap(loadedInstance.networkInterfaces, (networkInterface: any) =>
      (networkInterface.ipv6Addresses || []).map((address: any) => ({
        ip: address.ipv6Address,
        url: `http://${address.ipv6Address}:${instancePort}`,
      })),
    );
    loadedInstance.permanentIps = loadedInstance.networkInterfaces
      .filter((networkInterface: any) => networkInterface.attachment?.deleteOnTermination === false)
      .map((networkInterface: any) => networkInterface.privateIpAddress);
  }

  return loadedInstance;
}

function hasHealthState(instance: IAmazonLoadedInstance, healthProviderType: string, state: string): boolean {
  return (instance.health || []).some((health: any) => health.type === healthProviderType && health.state === state);
}

function canRegisterWithLoadBalancer(instance: IAmazonLoadedInstance): boolean {
  if (!instance.loadBalancers?.length) {
    return false;
  }
  return (
    hasHealthState(instance, 'LoadBalancer', 'OutOfService') ||
    !(instance.health || []).some((health: any) => health.type === 'LoadBalancer')
  );
}

function canRegisterWithTargetGroup(instance: IAmazonLoadedInstance): boolean {
  if (!instance.targetGroups?.length) {
    return false;
  }
  return (
    hasHealthState(instance, 'TargetGroup', 'OutOfService') ||
    !(instance.health || []).some((health: any) => health.type === 'TargetGroup')
  );
}

function canDeregisterFromTargetGroup(instance: IAmazonLoadedInstance): boolean {
  return (instance.health || []).some(
    (health: any) => health.type === 'TargetGroup' && health.state !== 'OutOfService',
  );
}

export function AmazonInstanceActions({
  app,
  instance,
}: {
  app: Application;
  instance: IAmazonLoadedInstance;
}): JSX.Element | null {
  if (!AWSProviderSettings.adHocInfraWritesEnabled) {
    return null;
  }

  const closeIfCurrentInstance = () => {
    if (ReactInjector.$state.includes('**.instanceDetails', { instanceId: instance.instanceId })) {
      ReactInjector.$state.go('^');
    }
  };
  const confirm = (
    header: string,
    buttonText: string,
    title: string,
    submitMethod: (params?: any) => PromiseLike<any>,
    closeOnComplete = false,
    extraConfig: any = {},
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
      ...extraConfig,
    });
  };

  const loadBalancerNames = (instance.loadBalancers || []).join(' and ');
  const targetGroupNames = (instance.targetGroups || []).join(' and ');
  const actions = [
    {
      label: 'Reboot',
      triggerAction: () =>
        confirm(
          `Really reboot ${instance.instanceId}?`,
          `Reboot ${instance.instanceId}`,
          `Rebooting ${instance.instanceId}`,
          (params = {}) => {
            if (app.attributes?.platformHealthOnlyShowOverride && app.attributes?.platformHealthOnly) {
              params.interestingHealthProviderNames = ['Amazon'];
            }
            return AmazonInstanceWriter.rebootInstance(instance, app, params);
          },
          false,
          {
            platformHealthOnlyShowOverride: app.attributes?.platformHealthOnlyShowOverride,
            platformHealthType: 'Amazon',
          },
        ),
    },
    {
      label: 'Terminate',
      triggerAction: () =>
        confirm(
          `Really terminate ${instance.instanceId}?`,
          `Terminate ${instance.instanceId}`,
          `Terminating ${instance.instanceId}`,
          () => AmazonInstanceWriter.terminateInstance(instance, app),
          true,
        ),
    },
  ];

  if (instance.serverGroup) {
    actions.push({
      label: 'Terminate and Shrink Server Group',
      triggerAction: () =>
        confirm(
          `Really terminate ${instance.instanceId} and shrink ${instance.serverGroup}?`,
          `Terminate ${instance.instanceId} and shrink ${instance.serverGroup}`,
          `Terminating ${instance.instanceId} and shrinking server group`,
          () => AmazonInstanceWriter.terminateInstanceAndShrinkServerGroup(instance, app),
          true,
        ),
    });
  }

  if (hasHealthState(instance, 'Discovery', 'OutOfService') && !instance.serverGroupDisabled) {
    actions.unshift({
      label: 'Enable In Discovery',
      triggerAction: () =>
        confirm(
          `Really enable ${instance.instanceId} in discovery?`,
          `Enable ${instance.instanceId}`,
          `Enabling ${instance.instanceId} in discovery`,
          () => AmazonInstanceWriter.enableInstanceInDiscovery(instance, app),
        ),
    });
  }

  if (hasHealthState(instance, 'Discovery', 'Up') || hasHealthState(instance, 'Discovery', 'Down')) {
    actions.unshift({
      label: 'Disable in Discovery',
      triggerAction: () =>
        confirm(
          `Really disable ${instance.instanceId} in discovery?`,
          `Disable ${instance.instanceId}`,
          `Disabling ${instance.instanceId} in discovery`,
          () => AmazonInstanceWriter.disableInstanceInDiscovery(instance, app),
        ),
    });
  }

  if (canRegisterWithLoadBalancer(instance)) {
    actions.unshift({
      label: 'Register with Load Balancer',
      triggerAction: () =>
        confirm(
          `Really register ${instance.instanceId} with ${loadBalancerNames}?`,
          `Register ${instance.instanceId}`,
          `Registering ${instance.instanceId} with ${loadBalancerNames}`,
          () => AmazonInstanceWriter.registerInstanceWithLoadBalancer(instance, app),
        ),
    });
  }

  if ((instance.health || []).some((health: any) => health.type === 'LoadBalancer')) {
    actions.unshift({
      label: 'Deregister from Load Balancer',
      triggerAction: () =>
        confirm(
          `Really deregister ${instance.instanceId} from ${loadBalancerNames}?`,
          `Deregister ${instance.instanceId}`,
          `Deregistering ${instance.instanceId} from ${loadBalancerNames}`,
          () => AmazonInstanceWriter.deregisterInstanceFromLoadBalancer(instance, app),
        ),
    });
  }

  if (canRegisterWithTargetGroup(instance)) {
    actions.unshift({
      label: 'Register with Target Group',
      triggerAction: () =>
        confirm(
          `Really register ${instance.instanceId} with ${targetGroupNames}?`,
          `Register ${instance.instanceId}`,
          `Registering ${instance.instanceId} with ${targetGroupNames}`,
          () => AmazonInstanceWriter.registerInstanceWithTargetGroup(instance, app),
        ),
    });
  }

  if (canDeregisterFromTargetGroup(instance)) {
    actions.unshift({
      label: 'Deregister from Target Group',
      triggerAction: () =>
        confirm(
          `Really deregister ${instance.instanceId} from ${targetGroupNames}?`,
          `Deregister ${instance.instanceId}`,
          `Deregistering ${instance.instanceId} from ${targetGroupNames}`,
          () => AmazonInstanceWriter.deregisterInstanceFromTargetGroup(instance, app),
        ),
    });
  }

  return <InstanceActions actions={actions} />;
}

export class AmazonInstanceDetails extends React.Component<
  IAmazonInstanceDetailsProps,
  { instance?: IAmazonLoadedInstance; loading: boolean }
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
    const retrieveWhenReady = app.isStandalone
      ? Promise.resolve()
      : Promise.all([app.serverGroups.ready(), app.loadBalancers.ready()]);
    retrieveWhenReady
      .then(() => {
        if (this.isUnmounted) {
          return;
        }
        this.retrieveInstance();
        if (!app.isStandalone && app.serverGroups?.onRefresh) {
          this.unsubscribeFromRefresh = app.serverGroups.onRefresh(null, this.retrieveInstance);
        }
      })
      .catch(() => {
        if (!this.isUnmounted) {
          this.closeDetails();
        }
      });
  }

  private closeDetails(): void {
    ReactInjector.$state.go('^', { allowModalToStayOpen: true }, { location: 'replace' });
  }

  public componentDidUpdate(prevProps: IAmazonInstanceDetailsProps): void {
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

  private getInstanceParams(props: IAmazonInstanceDetailsProps = this.props): IAmazonInstanceParams {
    const params = props.instance || props.$stateParams;
    return {
      account: params?.account,
      instanceId: params?.instanceId,
      provider: params?.provider,
      region: params?.region,
    };
  }

  private retrieveInstance = (clearInstance = false): void => {
    const requestId = ++this.activeRequestId;
    this.setState(clearInstance ? { instance: undefined, loading: true } : { loading: true });
    loadAmazonInstanceDetails({ app: this.props.app, instance: this.getInstanceParams() }).then(
      (instance) => {
        if (!this.isUnmounted && requestId === this.activeRequestId) {
          this.setState({ instance, loading: false });
        }
      },
      () => {
        if (!this.isUnmounted && requestId === this.activeRequestId) {
          if (this.props.app.isStandalone) {
            RecentHistoryService.removeLastItem('instances');
            this.setState({
              instance: ({
                instanceIdNotFound: this.getInstanceParams().instanceId,
              } as unknown) as IAmazonLoadedInstance,
              loading: false,
            });
          } else {
            this.closeDetails();
          }
        }
      },
    );
  };

  public render(): JSX.Element {
    const { app, environment, moniker } = this.props;
    const { instance, loading } = this.state;
    const instanceId = instance?.instanceId || instance?.instanceIdNotFound || this.getInstanceParams().instanceId;
    const instancePort = app.attributes?.instancePort || SETTINGS.defaultInstancePort || 80;

    return (
      <div className="details-panel">
        <div className="header">
          <InstanceDetailsHeader
            cloudProvider="aws"
            healthState={instance?.healthState}
            instanceId={instanceId}
            loading={loading}
            standalone={app.isStandalone}
          />
          {!loading && instance && !instance.instanceIdNotFound && instance.placement && (
            <div className="actions">
              <AmazonInstanceActions app={app} instance={instance} />
              <InstanceInsights insights={instance.insightActions} instance={instance} />
            </div>
          )}
        </div>
        {!loading && instance && !instance.instanceIdNotFound && (
          <div className="content">
            <AmazonInstanceInformation instance={instance} />
            <InstanceStatus
              healthMetrics={instance.healthMetrics || []}
              healthState={instance.healthState}
              metricTypes={['LoadBalancer', 'TargetGroup']}
              privateIpAddress={instance.privateIpAddress}
            />
            <CollapsibleSection heading="DNS">
              <InstanceDns
                instancePort={String(instance.instancePort || instancePort)}
                ipv6Addresses={instance.ipv6Addresses || []}
                permanentIps={instance.permanentIps || []}
                privateDnsName={instance.privateDnsName || ''}
                privateIpAddress={instance.privateIpAddress || ''}
                publicDnsName={instance.publicDnsName || ''}
                publicIpAddress={instance.publicIpAddress || ''}
              />
            </CollapsibleSection>
            <InstanceSecurityGroups instance={instance} />
            <InstanceTags tags={instance.tags || []} />
            {instance.baseIpAddress && (
              <CollapsibleSection heading="Console Output">
                <ul>
                  <li>
                    <ConsoleOutputLink instance={instance} />
                  </li>
                </ul>
              </CollapsibleSection>
            )}
            <InstanceLinks
              address={instance.baseIpAddress}
              application={app}
              instance={instance}
              moniker={moniker}
              environment={environment}
            />
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

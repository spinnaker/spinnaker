import React, { useLayoutEffect, useState } from 'react';

import {
  AccountTag,
  AngularServices,
  CollapsibleSection,
  ConfirmationModalService,
  Details,
  InstanceActions,
  InstanceReader,
  InstanceWriter,
  RecentHistoryService,
} from '@spinnaker/core';

import { GceXpnNamingService } from '../../common/xpnNaming.gce.service';

interface IGceInstanceDetailsProps {
  app: any;
  instance?: any;
  initialInstance?: any;
  $stateParams?: any;
}

function getInstanceParams(props: IGceInstanceDetailsProps): any {
  const stateParams = props.$stateParams || AngularServices.$stateParams || {};
  const instance = props.instance || props.initialInstance || {};
  return {
    account: instance.account || stateParams.account || stateParams.accountId,
    instanceId: instance.instanceId || stateParams.instanceId || stateParams.id,
    region: instance.region || stateParams.region,
  };
}

function findInstanceSummary(app: any, instanceId: string): any {
  const serverGroups = app?.serverGroups?.data || [];
  for (const serverGroup of serverGroups) {
    const summary = (serverGroup.instances || []).find((candidate: any) => candidate.id === instanceId);
    if (summary) {
      return {
        ...summary,
        account: serverGroup.account,
        loadBalancers: serverGroup.loadBalancers || [],
        region: serverGroup.region,
        serverGroup: serverGroup.name,
        vpcId: serverGroup.vpcId,
      };
    }
  }

  const loadBalancers = app?.loadBalancers?.data || [];
  for (const loadBalancer of loadBalancers) {
    const summary = (loadBalancer.instances || []).find((candidate: any) => candidate.id === instanceId);
    if (summary) {
      return {
        ...summary,
        account: loadBalancer.account,
        loadBalancers: [loadBalancer.name],
        region: loadBalancer.region,
        vpcId: loadBalancer.vpcId,
      };
    }

    for (const serverGroup of (loadBalancer.serverGroups || []).filter((candidate: any) => candidate.isDisabled)) {
      const disabledSummary = (serverGroup.instances || []).find((candidate: any) => candidate.id === instanceId);
      if (disabledSummary) {
        return {
          ...disabledSummary,
          account: loadBalancer.account,
          loadBalancers: [loadBalancer.name],
          region: loadBalancer.region,
          serverGroup: disabledSummary.serverGroup || serverGroup.name,
          serverGroupDisabled: true,
          vpcId: loadBalancer.vpcId,
        };
      }
    }
  }

  return null;
}

function projectFromSelfLink(selfLink: string): string | undefined {
  const match = /\/projects\/([^/]+)/.exec(selfLink || '');
  return match?.[1];
}

export function decorateInstance(instance: any): any {
  const xpnNaming = new GceXpnNamingService();
  const networkInterface = instance.networkInterfaces?.[0] || {};
  const project = projectFromSelfLink(instance.selfLink) || instance.account;
  const zone = instance.zone || instance.placement?.availabilityZone || instance.region;
  const network = xpnNaming.decorateXpnResourceIfNecessary(project, networkInterface.network);
  const subnet = xpnNaming.decorateXpnResourceIfNecessary(project, networkInterface.subnetwork);
  const externalIpAddress = networkInterface.accessConfigs?.[0]?.natIP;
  const internalIpAddress = networkInterface.networkIP;
  const sshLink = instance.selfLink
    ? instance.selfLink.replace('www.googleapis.com/compute/v1', 'cloudssh.developers.google.com')
    : null;

  return {
    ...instance,
    baseIpAddress: externalIpAddress || internalIpAddress,
    externalIpAddress,
    gcloudSSHCommand: zone ? `gcloud compute ssh ${instance.name || instance.instanceId} --zone ${zone}` : undefined,
    health: (instance.health || []).filter((health: any) => health.type !== 'Google' || health.state !== 'Unknown'),
    internalDnsName: instance.instanceId,
    internalIpAddress,
    logsLink: project
      ? `https://console.cloud.google.com/logs/query?project=${encodeURIComponent(project)}&resource=gce_instance`
      : undefined,
    network,
    region: instance.region || zone,
    sshLink,
    subnet,
  };
}

function hasHealthState(instance: any, type: string, state: string): boolean {
  return (instance.health || []).some((health: any) => health.type === type && health.state === state);
}

function getNetworkLoadBalancerNames(app: any, instance: any): string[] {
  if (!instance.loadBalancers?.length || !app.loadBalancers) {
    return [];
  }

  return (app.loadBalancers.data || [])
    .filter(
      (loadBalancer: any) =>
        loadBalancer.account === instance.account &&
        loadBalancer.loadBalancerType === 'NETWORK' &&
        instance.loadBalancers.includes(loadBalancer.name),
    )
    .map((loadBalancer: any) => loadBalancer.name);
}

function canRegisterWithLoadBalancer(app: any, instance: any): boolean {
  if (!getNetworkLoadBalancerNames(app, instance).length) {
    return false;
  }

  return (
    hasHealthState(instance, 'LoadBalancer', 'OutOfService') ||
    !(instance.health || []).some((health: any) => health.type === 'LoadBalancer')
  );
}

function canRegisterWithDiscovery(instance: any): boolean {
  const discoveryHealth = (instance.health || []).filter((health: any) => health.type === 'Discovery');
  return discoveryHealth[0]?.state === 'OutOfService';
}

export function GceInstanceActions({ app, instance }: { app: any; instance: any }): JSX.Element {
  const closeIfCurrentInstance = () => {
    if (AngularServices.$state.includes('**.instanceDetails', { instanceId: instance.instanceId })) {
      AngularServices.$state.go('^');
    }
  };
  const confirm = (
    header: string,
    buttonText: string,
    title: string,
    submitMethod: (params?: any) => PromiseLike<any>,
    closeOnComplete = false,
  ) => {
    ConfirmationModalService.confirm({
      account: instance.account,
      askForReason: true,
      buttonText,
      header,
      submitMethod,
      taskMonitorConfig: {
        application: app,
        onTaskComplete: closeOnComplete ? closeIfCurrentInstance : undefined,
        title,
      },
    });
  };
  const networkLoadBalancerNames = getNetworkLoadBalancerNames(app, instance);
  const loadBalancerInstance = { ...instance, loadBalancers: networkLoadBalancerNames };
  const loadBalancerNames = networkLoadBalancerNames.join(' and ');
  const actions = [];

  if (canRegisterWithDiscovery(instance)) {
    actions.push({
      label: 'Enable in Discovery',
      triggerAction: () =>
        confirm(
          `Really enable ${instance.instanceId} in discovery?`,
          `Enable ${instance.instanceId}`,
          `Enabling ${instance.instanceId} in discovery`,
          (params = {}) => InstanceWriter.enableInstanceInDiscovery(instance, app, params),
        ),
    });
  }

  if (hasHealthState(instance, 'Discovery', 'Up')) {
    actions.push({
      label: 'Disable in Discovery',
      triggerAction: () =>
        confirm(
          `Really disable ${instance.instanceId} in discovery?`,
          `Disable ${instance.instanceId}`,
          `Disabling ${instance.instanceId} in discovery`,
          (params = {}) => InstanceWriter.disableInstanceInDiscovery(instance, app, params),
        ),
    });
  }

  if (canRegisterWithLoadBalancer(app, instance)) {
    actions.push({
      label: 'Register with Load Balancer',
      triggerAction: () =>
        confirm(
          `Really register ${instance.instanceId} with ${loadBalancerNames}?`,
          `Register ${instance.instanceId}`,
          `Registering ${instance.instanceId} with ${loadBalancerNames}`,
          (params = {}) => InstanceWriter.registerInstanceWithLoadBalancer(loadBalancerInstance, app, params),
        ),
    });
  }

  if (
    networkLoadBalancerNames.length > 0 &&
    (instance.health || []).some((health: any) => health.type === 'LoadBalancer')
  ) {
    actions.push({
      label: 'Deregister from Load Balancer',
      triggerAction: () =>
        confirm(
          `Really deregister ${instance.instanceId} from ${loadBalancerNames}?`,
          `Deregister ${instance.instanceId}`,
          `Deregistering ${instance.instanceId} from ${loadBalancerNames}`,
          (params = {}) => InstanceWriter.deregisterInstanceFromLoadBalancer(loadBalancerInstance, app, params),
        ),
    });
  }

  actions.push({
    label: 'Reboot',
    triggerAction: () =>
      confirm(
        `Really reboot ${instance.instanceId}?`,
        `Reboot ${instance.instanceId}`,
        `Rebooting ${instance.instanceId}`,
        (params = {}) =>
          InstanceWriter.rebootInstance(instance, app, { ...params, interestingHealthProviderNames: [] }),
      ),
  });
  actions.push({
    label: 'Terminate',
    triggerAction: () =>
      confirm(
        `Really terminate ${instance.instanceId}?`,
        `Terminate ${instance.instanceId}`,
        `Terminating ${instance.instanceId}`,
        (params = {}) =>
          InstanceWriter.terminateInstance(instance, app, {
            ...params,
            cloudProvider: 'gce',
            ...(instance.serverGroup ? { managedInstanceGroupName: instance.serverGroup } : {}),
          }),
        true,
      ),
  });

  if (instance.serverGroup) {
    actions.push({
      label: 'Terminate and Shrink Server Group',
      triggerAction: () =>
        confirm(
          `Really terminate ${instance.instanceId} and shrink ${instance.serverGroup}?`,
          `Terminate ${instance.instanceId} and shrink ${instance.serverGroup}`,
          `Terminating ${instance.instanceId} and shrinking server group`,
          (params = {}) =>
            InstanceWriter.terminateInstanceAndShrinkServerGroup(instance, app, {
              ...params,
              instanceIds: [instance.instanceId],
              serverGroupName: instance.serverGroup,
              zone: instance.placement.availabilityZone,
            }),
          true,
        ),
    });
  }

  return <InstanceActions actions={actions} />;
}

export function GceInstanceDetails(props: IGceInstanceDetailsProps): JSX.Element {
  const [instance, setInstance] = useState<any>(props.initialInstance || null);
  const [loading, setLoading] = useState<boolean>(true);
  const [notFound, setNotFound] = useState<boolean>(false);

  useLayoutEffect(() => {
    let cancelled = false;
    const params = getInstanceParams(props);
    const summary = params.instanceId ? findInstanceSummary(props.app, params.instanceId) : null;
    const account = summary?.account || params.account;
    const region = summary?.region || params.region;

    setInstance(null);
    setLoading(true);
    setNotFound(false);

    if (!params.instanceId || !account || !region) {
      setNotFound(true);
      setLoading(false);
      return undefined;
    }

    RecentHistoryService.addExtraDataToLatest('instances', { account, region, serverGroup: summary?.serverGroup });

    InstanceReader.getInstanceDetails(account, region, params.instanceId).then(
      (details: any) => {
        if (cancelled) {
          return;
        }
        setInstance(decorateInstance({ ...summary, ...details, account, instanceId: params.instanceId, region }));
        setLoading(false);
      },
      () => {
        if (cancelled) {
          return;
        }
        setNotFound(true);
        setLoading(false);
      },
    );

    return () => {
      cancelled = true;
    };
  }, [props.app, props.instance, props.initialInstance, props.$stateParams]);

  if (notFound) {
    return <div className="alert alert-warning">Instance not found.</div>;
  }

  return (
    <Details loading={loading}>
      {instance && (
        <>
          <Details.Header
            icon={<span className="icon-gce" />}
            name={instance.name || instance.instanceId}
            actions={<GceInstanceActions app={props.app} instance={instance} />}
          />
          <Details.Content loading={loading}>
            <CollapsibleSection heading="Instance Information" defaultExpanded={true}>
              <dl className="dl-horizontal dl-narrow">
                <dt>Account</dt>
                <dd>
                  <AccountTag account={instance.account} />
                </dd>
                <dt>Region</dt>
                <dd>{instance.region}</dd>
                <dt>Type</dt>
                <dd>{instance.machineType || instance.instanceType || instance.type}</dd>
                <dt>Network</dt>
                <dd>{instance.network || '-'}</dd>
                <dt>Subnet</dt>
                <dd>{instance.subnet || '-'}</dd>
              </dl>
            </CollapsibleSection>
            <CollapsibleSection heading="DNS" defaultExpanded={true}>
              <dl className="dl-horizontal dl-narrow">
                <dt>Internal DNS Name</dt>
                <dd>{instance.internalDnsName || '-'}</dd>
                <dt>Internal IP</dt>
                <dd>{instance.internalIpAddress || '-'}</dd>
                <dt>External IP</dt>
                <dd>{instance.externalIpAddress || '-'}</dd>
              </dl>
            </CollapsibleSection>
            <CollapsibleSection heading="SSH" defaultExpanded={false}>
              {instance.sshLink && (
                <p>
                  <a href={instance.sshLink} target="_blank" rel="noopener noreferrer">
                    SSH in Google Cloud Console
                  </a>
                </p>
              )}
              {instance.gcloudSSHCommand && <pre>{instance.gcloudSSHCommand}</pre>}
            </CollapsibleSection>
            <CollapsibleSection heading="Logs" defaultExpanded={false}>
              {instance.logsLink ? (
                <a href={instance.logsLink} target="_blank" rel="noopener noreferrer">
                  View Google Cloud logs
                </a>
              ) : (
                <span>No log link available</span>
              )}
            </CollapsibleSection>
          </Details.Content>
        </>
      )}
    </Details>
  );
}

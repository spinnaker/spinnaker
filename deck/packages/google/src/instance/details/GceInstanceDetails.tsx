import React, { useEffect, useState } from 'react';

import {
  AccountTag,
  CollapsibleSection,
  Details,
  InstanceReader,
  ReactInjector,
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
  const stateParams = props.$stateParams || ReactInjector.$stateParams || {};
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

export function GceInstanceDetails(props: IGceInstanceDetailsProps): JSX.Element {
  const [instance, setInstance] = useState<any>(props.initialInstance || null);
  const [loading, setLoading] = useState<boolean>(true);
  const [notFound, setNotFound] = useState<boolean>(false);

  useEffect(() => {
    let cancelled = false;
    const params = getInstanceParams(props);
    const summary = params.instanceId ? findInstanceSummary(props.app, params.instanceId) : null;
    const account = params.account || summary?.account;
    const region = params.region || summary?.region;

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
            actions={<span className="text-muted">Google Instance</span>}
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

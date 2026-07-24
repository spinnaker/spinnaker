import React, { useCallback, useEffect, useState } from 'react';

import {
  AccountService,
  AccountTag,
  CloudProviderRegistry,
  CollapsibleSection,
  ConfirmationModalService,
  InfrastructureCaches,
  LoadBalancerWriter,
  ManagedMenuItem,
  TaskExecutor,
  useDataSource,
  useDeckRuntimeServices,
} from '@spinnaker/core';

import { GceLoadBalancerChoiceModal } from '../configure/choice/GceLoadBalancerChoiceModal';
import { GceHttpLoadBalancerUtils } from '../httpLoadBalancerUtils.service';

interface IUseDetailsProps {
  app: any;
  autoClose: () => void;
  loadBalancerParams: any;
}

interface ILoadGceLoadBalancerDetailsProps extends IUseDetailsProps {
  accountService?: typeof AccountService;
  loadBalancers?: any[];
  loadBalancerReader: any;
}

const gceHttpLoadBalancerUtils = new GceHttpLoadBalancerUtils();

function isSameVpc(candidate: any, params: any): boolean {
  return (candidate.vpcId || candidate.vpcid || null) === (params.vpcId || null);
}

function findLoadBalancerInList(loadBalancers: any[], params: any): any {
  return (loadBalancers || []).find((candidate: any) => {
    return (
      candidate.name === params.name &&
      candidate.account === params.accountId &&
      (candidate.region === params.region || candidate.region === 'global') &&
      isSameVpc(candidate, params)
    );
  });
}

function uniqBy<T>(items: T[], getKey: (item: T) => string): T[] {
  const seen = new Set<string>();
  return items.filter((item) => {
    const key = getKey(item);
    if (seen.has(key)) {
      return false;
    }
    seen.add(key);
    return true;
  });
}

function getProtocol(loadBalancer: any): string {
  const port = loadBalancer?.listenerDescriptions?.[0]?.listener?.loadBalancerPort;
  return port === '443' ? 'https:' : 'http:';
}

function getBackendServices(loadBalancer: any): any[] {
  let backendServices = [loadBalancer.defaultService].filter(Boolean);

  if ((loadBalancer.hostRules || []).length) {
    (loadBalancer.hostRules || []).forEach((hostRule: any) => {
      if (hostRule.pathMatcher?.defaultService) {
        backendServices.push(hostRule.pathMatcher.defaultService);
      }
      backendServices = backendServices.concat(
        (hostRule.pathMatcher?.pathRules || []).map((pathRule: any) => pathRule.backendService).filter(Boolean),
      );
    });
  }

  return uniqBy(backendServices, (backendService: any) => backendService.name || backendService.selfLink || '');
}

function getResourceTypes(loadBalancerType: string): string[] {
  switch (loadBalancerType) {
    case 'INTERNAL':
      return ['gce_forwarding_rule', 'gce_backend_service'];
    case 'NETWORK':
      return ['gce_forwarding_rule', 'gce_target_pool', 'gce_health_check'];
    case 'SSL':
    case 'TCP':
      return ['gce_forwarding_rule', 'gce_backend_service'];
    case 'INTERNAL_MANAGED':
      return ['http_load_balancer', 'gce_target_http_proxy', 'gce_url_map', 'gce_backend_service'];
    default:
      return ['http_load_balancer', 'gce_target_http_proxy', 'gce_url_map', 'gce_backend_service'];
  }
}

function buildLogsLink(project: string, loadBalancer: any): string {
  return `https://console.developers.google.com/project/${project}/logs?advancedFilter=resource.type=(${getResourceTypes(
    loadBalancer.loadBalancerType,
  ).join(' OR ')})%0A"${loadBalancer.name}"`;
}

async function loadDetails(loadBalancer: any, loadBalancerParams: any, loadBalancerReader: any): Promise<any[]> {
  const provider =
    loadBalancer.provider || loadBalancer.cloudProvider || loadBalancer.type || loadBalancerParams.provider;
  const region = loadBalancer.region || loadBalancerParams.region;
  if (gceHttpLoadBalancerUtils.isHttpLoadBalancer(loadBalancer)) {
    const detailSets = await Promise.all(
      (loadBalancer.listeners || []).map((listener: any) =>
        loadBalancerReader.getLoadBalancerDetails(
          provider,
          loadBalancerParams.accountId,
          region,
          listener.name || listener,
        ),
      ),
    );
    const loadBalancers = ([] as any[]).concat(...detailSets);
    const representativeLoadBalancer = loadBalancers[0];

    if (!representativeLoadBalancer) {
      return [];
    }

    representativeLoadBalancer.dns = uniqBy(
      loadBalancers
        .map((detail: any) => ({ dnsname: detail.dnsname, protocol: getProtocol(detail) }))
        .filter((detail: any) => detail.dnsname),
      (detail: any) => detail.dnsname,
    );
    representativeLoadBalancer.listenerDescriptions = ([] as any[]).concat(
      ...loadBalancers.map((detail: any) => detail.listenerDescriptions || []),
    );
    return [representativeLoadBalancer];
  }

  const details = await loadBalancerReader.getLoadBalancerDetails(
    provider,
    loadBalancerParams.accountId,
    region,
    loadBalancer.name,
  );
  if (details[0]) {
    details[0].dns = { dnsname: details[0].dnsname, protocol: getProtocol(details[0]) };
  }
  return details;
}

export async function loadGceLoadBalancerDetails({
  accountService = AccountService,
  app,
  autoClose,
  loadBalancers,
  loadBalancerParams,
  loadBalancerReader,
}: ILoadGceLoadBalancerDetailsProps): Promise<any | undefined> {
  const loadBalancer = findLoadBalancerInList(loadBalancers || app?.loadBalancers?.data || [], loadBalancerParams);

  if (!loadBalancer) {
    autoClose();
    return undefined;
  }

  const details = await loadDetails(loadBalancer, loadBalancerParams, loadBalancerReader);
  const filtered = details.filter((detail: any) => (detail.vpcid || null) === (loadBalancerParams.vpcId || null));
  if (filtered.length) {
    loadBalancer.elb = filtered[0];
    loadBalancer.account = loadBalancerParams.accountId;

    if (gceHttpLoadBalancerUtils.isHttpLoadBalancer(loadBalancer)) {
      loadBalancer.elb.backendServices = getBackendServices(loadBalancer);
      loadBalancer.elb.healthChecks = uniqBy(
        loadBalancer.elb.backendServices.map((backendService: any) => backendService.healthCheck).filter(Boolean),
        (healthCheck: any) => healthCheck.name || healthCheck.selfLink || '',
      );
    }
  }

  const accountDetails: any = await accountService.getAccountDetails(loadBalancerParams.accountId);
  if (accountDetails?.project) {
    loadBalancer.project = accountDetails.project;
    loadBalancer.logsLink = buildLogsLink(accountDetails.project, loadBalancer);
  }

  return loadBalancer;
}

export function useGceLoadBalancerDetails({ app, autoClose, loadBalancerParams }: IUseDetailsProps): any {
  const { loadBalancerReader } = useDeckRuntimeServices();
  const dataSource = app.getDataSource('loadBalancers');
  const { data: loadBalancers, loaded, refresh, error } = useDataSource<any[]>(dataSource);
  const [data, setData] = useState<any>();
  const [loadingDetails, setLoadingDetails] = useState(false);
  const [detailsError, setDetailsError] = useState<string | null>(null);

  const refetch = useCallback(async () => {
    if (!loaded || error) {
      return;
    }

    setLoadingDetails(true);
    setDetailsError(null);
    try {
      setData(
        await loadGceLoadBalancerDetails({
          app,
          autoClose,
          loadBalancers,
          loadBalancerParams,
          loadBalancerReader,
        }),
      );
    } catch (e) {
      setDetailsError(e?.message || String(e));
    } finally {
      setLoadingDetails(false);
    }
  }, [app, autoClose, error, loadBalancerReader, loadBalancers, loadBalancerParams, loaded]);

  useEffect(() => {
    refetch();
  }, [refetch]);

  return {
    data,
    error: error || detailsError,
    loading: !loaded || loadingDetails,
    refetch: async () => {
      refresh();
    },
  };
}

function deleteGceHttpLoadBalancer(loadBalancer: any, app: any, params: any = {}): PromiseLike<any> {
  const region = loadBalancer.region || 'global';
  const job = {
    cloudProvider: loadBalancer.provider || 'gce',
    credentials: loadBalancer.account,
    loadBalancerName: loadBalancer.listeners?.[0]?.name || loadBalancer.name,
    loadBalancerType: loadBalancer.loadBalancerType,
    region,
    regions: [region],
    type: 'deleteLoadBalancer',
    ...params,
  };

  InfrastructureCaches.clearCache('backendServices');
  InfrastructureCaches.clearCache('healthChecks');

  return TaskExecutor.executeTask({
    application: app,
    description: `Delete load balancer: ${loadBalancer.urlMapName || loadBalancer.name} in ${
      loadBalancer.account
    }:global`,
    job: [job],
  });
}

function deleteGceLoadBalancer(loadBalancer: any, app: any, params: any = {}): PromiseLike<any> {
  if (gceHttpLoadBalancerUtils.isHttpLoadBalancer(loadBalancer)) {
    return deleteGceHttpLoadBalancer(loadBalancer, app, params);
  }

  const region = loadBalancer.region || 'global';
  return LoadBalancerWriter.deleteLoadBalancer(
    {
      accountName: loadBalancer.account,
      cloudProvider: 'gce',
      credentials: loadBalancer.account,
      deleteHealthChecks: false,
      loadBalancerName: loadBalancer.name,
      loadBalancerType: loadBalancer.loadBalancerType || 'NETWORK',
      region,
      regions: [region],
      vpcId: loadBalancer.vpcId,
      ...params,
    },
    app,
  );
}

function GceLoadBalancerDeleteOptions({
  deleteParams,
  loadBalancer,
}: {
  deleteParams: { deleteHealthChecks: boolean };
  loadBalancer: any;
}): JSX.Element {
  const hasHealthChecks = gceHttpLoadBalancerUtils.isHttpLoadBalancer(loadBalancer) || !!loadBalancer.healthCheck;
  if (!hasHealthChecks) {
    return null;
  }

  return (
    <label className="checkbox-inline">
      <input
        type="checkbox"
        onChange={(event) => {
          deleteParams.deleteHealthChecks = event.currentTarget.checked;
        }}
      />
      Delete associated health checks
    </label>
  );
}

export function GceLoadBalancerActions({ app, loadBalancer }: { app: any; loadBalancer: any }): JSX.Element {
  if (CloudProviderRegistry.isDisabled('gce')) {
    return null;
  }

  const hasInstances = (loadBalancer.instances || []).length > 0;
  const editLoadBalancer = () => {
    GceLoadBalancerChoiceModal.show({
      app,
      application: app,
      forPipelineConfig: false,
      isNew: false,
      loadBalancer,
      mode: 'edit',
    } as any);
  };
  const deleteLoadBalancer = () => {
    const deleteParams = { deleteHealthChecks: false };
    ConfirmationModalService.confirm({
      account: loadBalancer.account,
      bodyContent: <GceLoadBalancerDeleteOptions deleteParams={deleteParams} loadBalancer={loadBalancer} />,
      buttonText: `Delete ${loadBalancer.name}`,
      header: `Really delete ${loadBalancer.name}?`,
      submitMethod: (params: any = {}) =>
        deleteGceLoadBalancer(loadBalancer, app, { deleteHealthChecks: deleteParams.deleteHealthChecks, ...params }),
      taskMonitorConfig: { application: app, title: `Deleting ${loadBalancer.name}` },
    });
  };

  return (
    <div className="dropdown" id="gce-load-balancer-actions-dropdown">
      <button className="btn btn-sm btn-primary dropdown-toggle" data-toggle="dropdown" type="button">
        Load Balancer Actions
      </button>
      <ul className="dropdown-menu">
        <ManagedMenuItem application={app} resource={loadBalancer} onClick={editLoadBalancer}>
          Edit Load Balancer
        </ManagedMenuItem>
        {!hasInstances && (
          <ManagedMenuItem application={app} resource={loadBalancer} onClick={deleteLoadBalancer}>
            Delete Load Balancer
          </ManagedMenuItem>
        )}
        {hasInstances && (
          <li className="disabled" title="You must detach all instances before deleting this load balancer.">
            <a>Delete Load Balancer</a>
          </li>
        )}
      </ul>
    </div>
  );
}

export function GceLoadBalancerInformationSection({ loadBalancer }: { app: any; loadBalancer: any }): JSX.Element {
  return (
    <CollapsibleSection heading="Load Balancer Information" defaultExpanded={true}>
      <dl className="dl-horizontal dl-narrow">
        <dt>Account</dt>
        <dd>
          <AccountTag account={loadBalancer.account} />
        </dd>
        <dt>Region</dt>
        <dd>{loadBalancer.region || 'global'}</dd>
        <dt>Type</dt>
        <dd>{loadBalancer.loadBalancerType || loadBalancer.providerType || '-'}</dd>
        {loadBalancer.project && (
          <>
            <dt>Project</dt>
            <dd>{loadBalancer.project}</dd>
          </>
        )}
        {loadBalancer.elb?.dns && (
          <>
            <dt>DNS</dt>
            <dd>
              {Array.isArray(loadBalancer.elb.dns)
                ? loadBalancer.elb.dns.map((dns: any) => `${dns.protocol}//${dns.dnsname}`).join(', ')
                : `${loadBalancer.elb.dns.protocol}//${loadBalancer.elb.dns.dnsname}`}
            </dd>
          </>
        )}
      </dl>
    </CollapsibleSection>
  );
}

export function GceLoadBalancerListenersSection({ loadBalancer }: { app: any; loadBalancer: any }): JSX.Element {
  return (
    <CollapsibleSection heading="Listeners" defaultExpanded={true}>
      {(loadBalancer.listeners || []).length ? (
        <ul>
          {loadBalancer.listeners.map((listener: any, index: number) => (
            <li key={index}>{[listener.protocol, listener.port || listener.portRange].filter(Boolean).join(':')}</li>
          ))}
        </ul>
      ) : (
        <span>No listeners configured</span>
      )}
    </CollapsibleSection>
  );
}

export function GceLoadBalancerBackendServicesSection({ loadBalancer }: { app: any; loadBalancer: any }): JSX.Element {
  const backendServices = loadBalancer.elb?.backendServices || [];
  const healthChecks = loadBalancer.elb?.healthChecks || [];

  if (!backendServices.length && !healthChecks.length) {
    return null;
  }

  return (
    <CollapsibleSection heading="Backend Services" defaultExpanded={true}>
      {backendServices.length > 0 && (
        <dl className="dl-horizontal dl-narrow">
          <dt>Services</dt>
          <dd>{backendServices.map((backendService: any) => backendService.name).join(', ')}</dd>
        </dl>
      )}
      {healthChecks.length > 0 && (
        <dl className="dl-horizontal dl-narrow">
          <dt>Health Checks</dt>
          <dd>{healthChecks.map((healthCheck: any) => healthCheck.name).join(', ')}</dd>
        </dl>
      )}
    </CollapsibleSection>
  );
}

export function GceLoadBalancerLogsSection({ loadBalancer }: { app: any; loadBalancer: any }): JSX.Element {
  return (
    <CollapsibleSection heading="Logs" defaultExpanded={false}>
      {loadBalancer.logsLink ? (
        <a href={loadBalancer.logsLink} target="_blank" rel="noopener noreferrer">
          View Google Cloud logs
        </a>
      ) : (
        <span>No log link available</span>
      )}
    </CollapsibleSection>
  );
}

export const gceLoadBalancerDetailsSections = [
  GceLoadBalancerInformationSection,
  GceLoadBalancerListenersSection,
  GceLoadBalancerBackendServicesSection,
  GceLoadBalancerLogsSection,
];

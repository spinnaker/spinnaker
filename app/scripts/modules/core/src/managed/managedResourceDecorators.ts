import { getKindName, getResourceKindForLoadBalancerType } from './ManagedReader';
import { Application } from '../application';
import { SETTINGS } from '../config';
import { ILoadBalancer, IManagedResourceSummary, ISecurityGroup, IServerGroup } from '../domain';
import { IMoniker } from '../naming';

const isMonikerEqual = (a?: IMoniker, b?: IMoniker) =>
  a && b && a.app === b.app && a.stack === b.stack && a.detail === b.detail;

const getResourcesOfKind = (application: Application, kinds: string[]) => {
  const resources: IManagedResourceSummary[] = application.managedResources.data.resources;
  return resources.filter(({ kind }) => kinds.includes(getKindName(kind)));
};

export const addManagedResourceMetadataToServerGroups = (application: Application) => {
  if (!SETTINGS.feature.managedResources) {
    return;
  }

  const clusterResources = getResourcesOfKind(application, ['cluster']);
  const serverGroups: IServerGroup[] = application.serverGroups.data;

  serverGroups.forEach((serverGroup) => {
    const matchingResource = clusterResources.find(
      ({ moniker, locations: { account, regions } }) =>
        isMonikerEqual(moniker, serverGroup.moniker) &&
        account === serverGroup.account &&
        regions.some(({ name }) => name === serverGroup.region),
    );

    serverGroup.isManaged = !!matchingResource;
    serverGroup.managedResourceSummary = matchingResource;
  });
  application.serverGroups.dataUpdated();
};

export const addManagedResourceMetadataToLoadBalancers = (application: Application) => {
  if (!SETTINGS.feature.managedResources) {
    return;
  }

  const loadBalancerResources = getResourcesOfKind(application, ['classic-load-balancer', 'application-load-balancer']);
  const loadBalancers: ILoadBalancer[] = application.loadBalancers.data;

  loadBalancers.forEach((loadBalancer) => {
    const matchingResource = loadBalancerResources.find(
      ({ kind, moniker, locations: { account, regions } }) =>
        loadBalancer.loadBalancerType &&
        getKindName(kind) === getResourceKindForLoadBalancerType(loadBalancer.loadBalancerType) &&
        isMonikerEqual(moniker, loadBalancer.moniker) &&
        account === loadBalancer.account &&
        regions.some(({ name }) => name === loadBalancer.region),
    );

    loadBalancer.isManaged = !!matchingResource;
    loadBalancer.managedResourceSummary = matchingResource;
  });
  application.loadBalancers.dataUpdated();
};

export const addManagedResourceMetadataToSecurityGroups = (application: Application) => {
  if (!SETTINGS.feature.managedResources) {
    return;
  }

  const securityGroupResources = getResourcesOfKind(application, ['security-group']);
  const securityGroups: ISecurityGroup[] = application.securityGroups.data;

  securityGroups.forEach((securityGroup) => {
    const matchingResource = securityGroupResources.find(
      ({ moniker, locations: { account, regions } }) =>
        isMonikerEqual(moniker, securityGroup.moniker) &&
        account === securityGroup.account &&
        regions.some(({ name }) => name === securityGroup.region),
    );

    securityGroup.isManaged = !!matchingResource;
    securityGroup.managedResourceSummary = matchingResource;
  });
  application.securityGroups.dataUpdated();
};

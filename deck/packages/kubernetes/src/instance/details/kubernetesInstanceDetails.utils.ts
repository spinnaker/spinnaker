import { flattenDeep } from 'lodash';

import type { Application, ILoadBalancer } from '@spinnaker/core';

import type { IKubernetesInstance } from '../../interfaces';

export interface IKubernetesInstanceIdentifier {
  account: string;
  id: string;
  name: string;
  namespace: string;
}

export interface IKubernetesInstanceRecentHistoryData {
  account: string;
  region: string;
  serverGroup?: string;
}

interface InstanceManager {
  account: string;
  region: string;
  category: string;
  name: string;
  instances?: IKubernetesInstance[];
}

export function findKubernetesInstanceIdentifier(
  app: Application,
  instanceId: string,
  addRecentHistoryData: (extraData: IKubernetesInstanceRecentHistoryData) => void = () => null,
): IKubernetesInstanceIdentifier | null {
  const instanceManager = getInstanceManagers(app).find((dataSource) =>
    (dataSource.instances || []).some((possibleMatch) => possibleMatch.id === instanceId),
  );

  if (!instanceManager) {
    return null;
  }

  const instance = (instanceManager.instances || []).find((possibleMatch) => possibleMatch.id === instanceId);
  if (!instance) {
    return null;
  }

  const recentHistoryExtraData: IKubernetesInstanceRecentHistoryData = {
    region: instanceManager.region,
    account: instanceManager.account,
  };

  if (instanceManager.category === 'serverGroup') {
    recentHistoryExtraData.serverGroup = instanceManager.name;
  }

  addRecentHistoryData(recentHistoryExtraData);

  return {
    id: instance.id,
    name: instance.name,
    namespace: instanceManager.region,
    account: instanceManager.account,
  };
}

function getInstanceManagers(app: Application): InstanceManager[] {
  const loadBalancers = getDataSourceData<ILoadBalancer>(app, 'loadBalancers');

  return flattenDeep([
    getDataSourceData<InstanceManager>(app, 'serverGroups'),
    loadBalancers,
    loadBalancers.map((loadBalancer) => loadBalancer.serverGroups || []),
  ]).filter(Boolean) as InstanceManager[];
}

function getDataSourceData<T>(app: Application, key: string): T[] {
  return (app.getDataSource(key)?.data || []) as T[];
}

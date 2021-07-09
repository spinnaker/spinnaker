import { IOwnerOption } from './EntityTagEditor';
import { IServerGroup, IServerGroupManager } from '../domain';

export class ClusterTargetBuilder {
  public static buildClusterTargets(serverGroup: IServerGroup): IOwnerOption[] {
    const clusterName = serverGroup.moniker.cluster;
    const regionName = serverGroup.cloudProvider === 'kubernetes' ? 'namespace' : 'region';
    return [
      {
        type: 'serverGroup',
        label: `just ${serverGroup.name}`,
        owner: serverGroup,
        isDefault: true,
      },
      {
        type: 'cluster',
        label: `all server groups in **${serverGroup.region}** in ${clusterName}`,
        owner: {
          name: clusterName,
          cloudProvider: serverGroup.cloudProvider,
          region: serverGroup.region,
          account: serverGroup.account,
        },
        isDefault: false,
      },
      {
        type: 'cluster',
        label: `all server groups in **all ${regionName}s** in ${clusterName}`,
        owner: {
          name: clusterName,
          cloudProvider: serverGroup.cloudProvider,
          region: '*',
          account: serverGroup.account,
        },
        isDefault: false,
      },
    ];
  }

  public static buildManagerClusterTargets(serverGroupManager: IServerGroupManager): IOwnerOption[] {
    const clusterName = serverGroupManager.moniker.cluster;
    return [
      {
        type: 'serverGroupManager',
        label: `all server groups in **${serverGroupManager.name}** in ${clusterName}`,
        owner: {
          name: serverGroupManager.name,
          cloudProvider: serverGroupManager.cloudProvider,
          region: serverGroupManager.region,
          account: serverGroupManager.account,
        },
        isDefault: true,
      },
      {
        type: 'cluster',
        label: `all server groups in **${serverGroupManager.region}** in ${clusterName}`,
        owner: {
          name: clusterName,
          cloudProvider: serverGroupManager.cloudProvider,
          region: serverGroupManager.region,
          account: serverGroupManager.account,
        },
        isDefault: true,
      },
      {
        type: 'cluster',
        label: `all server groups in **all namespaces** in ${clusterName}`,
        owner: {
          name: clusterName,
          cloudProvider: serverGroupManager.cloudProvider,
          region: '*',
          account: serverGroupManager.account,
        },
        isDefault: false,
      },
    ];
  }
}

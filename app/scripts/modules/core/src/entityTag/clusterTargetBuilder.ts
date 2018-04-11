import { IServerGroup } from 'core/domain';
import { NameUtils } from 'core/naming';

import { IOwnerOption } from './EntityTagEditor';

export class ClusterTargetBuilder {
  public static buildClusterTargets(serverGroup: IServerGroup): IOwnerOption[] {
    const clusterName = NameUtils.getClusterNameFromServerGroupName(serverGroup.name);
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
          cloudProvider: 'aws',
          region: serverGroup.region,
          account: serverGroup.account,
        },
        isDefault: false,
      },
      {
        type: 'cluster',
        label: `all server groups in **all regions** in ${clusterName}`,
        owner: {
          name: clusterName,
          cloudProvider: 'aws',
          region: '*',
          account: serverGroup.account,
        },
        isDefault: false,
      },
    ];
  }
}

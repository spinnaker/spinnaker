import {module} from 'angular';

import {NAMING_SERVICE, NamingService} from 'core/naming/naming.service';
import {ServerGroup} from '../domain/serverGroup';
import {IOwnerOption} from './entityTagEditor.controller';

export class ClusterTargetBuilder {

  constructor(private namingService: NamingService) { 'ngInject'; }

  public buildClusterTargets(serverGroup: ServerGroup): IOwnerOption[] {
    const clusterName = this.namingService.getClusterNameFromServerGroupName(serverGroup.name);
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

export const CLUSTER_TARGET_BUILDER = 'spinnaker.core.entityTag.clusterTargetBuilder';
module(CLUSTER_TARGET_BUILDER, [
  NAMING_SERVICE,
]).service('clusterTargetBuilder', ClusterTargetBuilder);

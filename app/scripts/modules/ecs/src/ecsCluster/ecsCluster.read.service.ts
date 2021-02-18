import { module } from 'angular';

import { REST } from '@spinnaker/core';

import { IEcsCapacityProviderDetails } from './IEcsCapacityProviderDetails';
import { IEcsClusterDescriptor } from './IEcsCluster';

export class EcsClusterReader {
  public listClusters(): PromiseLike<IEcsClusterDescriptor[]> {
    return REST('/ecs/ecsClusters').get();
  }

  public describeClusters(account: string, region: string): PromiseLike<IEcsCapacityProviderDetails[]> {
    if (account != null && region != null) {
      return REST('/ecs/ecsClusterDescriptions').path(account).path(region).get();
    }
    return {} as PromiseLike<IEcsCapacityProviderDetails[]>;
  }
}

export const ECS_CLUSTER_READ_SERVICE = 'spinnaker.ecs.ecsCluster.read.service';

module(ECS_CLUSTER_READ_SERVICE, []).service('ecsClusterReader', EcsClusterReader);

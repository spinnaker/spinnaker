import { module } from 'angular';

import { REST } from '@spinnaker/core';
import { IEcsClusterDescriptor } from './IEcsCluster';

export class EcsClusterReader {
  public listClusters(): PromiseLike<IEcsClusterDescriptor[]> {
    return REST('/ecs/ecsClusters').get();
  }
}

export const ECS_CLUSTER_READ_SERVICE = 'spinnaker.ecs.ecsCluster.read.service';

module(ECS_CLUSTER_READ_SERVICE, []).service('ecsClusterReader', EcsClusterReader);

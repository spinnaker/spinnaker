import { module } from 'angular';

import { API } from '@spinnaker/core';
import { IEcsClusterDescriptor } from './IEcsCluster';

export class EscClusterReader {
  public listClusters(): ng.IPromise<IEcsClusterDescriptor[]> {
    return API.all('ecs')
      .all('ecsClusters')
      .getList();
  }
}

export const ECS_CLUSTER_READ_SERVICE = 'spinnaker.ecs.ecsCluster.read.service';

module(ECS_CLUSTER_READ_SERVICE, []).service('ecsClusterReader', EscClusterReader);

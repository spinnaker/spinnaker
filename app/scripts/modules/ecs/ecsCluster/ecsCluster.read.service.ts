import { module } from 'angular';

import { API_SERVICE, Api } from 'core/api/api.service';
import { IEcsClusterDescriptor } from './IEcsCluster';

export class EscClusterReader {
  public constructor(private API: Api) {
    'ngInject';
  }

  public listClusters(): ng.IPromise<IEcsClusterDescriptor[]> {
    return this.API.all('ecs')
      .all('ecsClusters')
      .getList();
  }
}

export const ECS_CLUSTER_READ_SERVICE = 'spinnaker.ecs.ecsCluster.read.service';

module(ECS_CLUSTER_READ_SERVICE, [API_SERVICE]).service('ecsClusterReader', EscClusterReader);

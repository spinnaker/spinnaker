import { module, IQService, IPromise } from 'angular';
import { chain, camelCase } from 'lodash';

import { IServerGroup, IInstanceCounts } from '@spinnaker/core';

import { IKubernetesLoadBalancer } from './details/IKubernetesLoadBalancer';

class KubernetesV2LoadBalancerTransformer {
  constructor(private $q: IQService) {
    'ngInject';
  }

  public normalizeLoadBalancer(loadBalancer: IKubernetesLoadBalancer): IPromise<IKubernetesLoadBalancer> {
    loadBalancer.provider = loadBalancer.type;
    loadBalancer.instances = [];
    loadBalancer.instanceCounts = this.buildInstanceCounts(loadBalancer.serverGroups);
    (loadBalancer.serverGroups || []).forEach(serverGroup => {
      serverGroup.cloudProvider = loadBalancer.provider;
      (serverGroup.instances || []).forEach(instance => {
        instance.cloudProvider = loadBalancer.provider;
      });
    });
    return this.$q.resolve(loadBalancer);
  }

  private buildInstanceCounts(serverGroups: IServerGroup[]): IInstanceCounts {
    const instanceCounts = chain(serverGroups)
      .map(serverGroup => serverGroup.instances)
      .flatten()
      .reduce(
        (acc: IInstanceCounts, instance: any) => {
          acc[camelCase(instance.health.state) as keyof IInstanceCounts]++;
          return acc;
        },
        {
          up: 0,
          down: 0,
          outOfService: 0,
          succeeded: 0,
          failed: 0,
          unknown: 0,
          starting: 0,
        },
      )
      .value();

    instanceCounts.outOfService += chain(serverGroups)
      .map(serverGroup => serverGroup.detachedInstances)
      .flatten()
      .value().length;

    return instanceCounts;
  }
}

export const KUBERNETES_V2_LOAD_BALANCER_TRANSFORMER = 'spinnaker.kubernetes.v2.loadBalancerTransformer';
module(KUBERNETES_V2_LOAD_BALANCER_TRANSFORMER, []).service(
  'kubernetesV2LoadBalancerTransformer',
  KubernetesV2LoadBalancerTransformer,
);

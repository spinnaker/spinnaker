import { chain } from 'lodash';

import type { IInstance, IInstanceCounts, ILoadBalancer } from '@spinnaker/core';

export interface IHaProxyBind {
  address?: string;
  port?: number;
  ssl?: boolean;
}

export interface IHaProxyLoadBalancer extends ILoadBalancer {
  mode?: string;
  defaultBackend?: string;
  binds?: Record<string, IHaProxyBind>;
  metadata?: Record<string, any>;
}

export class HaProxyLoadBalancerTransformer {
  public normalizeLoadBalancer(loadBalancer: IHaProxyLoadBalancer): PromiseLike<ILoadBalancer> {
    loadBalancer.provider = loadBalancer.type;
    loadBalancer.serverGroups = loadBalancer.serverGroups || [];
    loadBalancer.serverGroups.forEach((serverGroup) => {
      serverGroup.account = loadBalancer.account;
      serverGroup.region = loadBalancer.region;
      serverGroup.cloudProvider = loadBalancer.provider;
      serverGroup.instances = (serverGroup.instances || []).map((instance: any) =>
        this.transformInstance(instance, loadBalancer),
      );
    });

    const activeServerGroups = loadBalancer.serverGroups.filter((sg) => !sg.isDisabled);
    loadBalancer.instances = chain(activeServerGroups).map('instances').flatten().value() as IInstance[];
    loadBalancer.instanceCounts = this.buildInstanceCounts(loadBalancer.instances);
    return Promise.resolve(loadBalancer);
  }

  private transformInstance(instance: any, loadBalancer: ILoadBalancer): IInstance {
    // clouddriver attaches a single HaProxyServer health map per backend server
    const health = instance.health || {};
    instance.provider = loadBalancer.type;
    instance.account = loadBalancer.account;
    instance.region = loadBalancer.region;
    instance.healthState = health.state || 'Unknown';
    instance.health = [health];
    return instance as IInstance;
  }

  private buildInstanceCounts(instances: IInstance[]): IInstanceCounts {
    const counts: Record<string, number> = chain(instances).countBy('healthState').value();
    return {
      up: counts.Up || 0,
      down: counts.Down || 0,
      outOfService: counts.OutOfService || 0,
      starting: counts.Starting || 0,
      succeeded: counts.Succeeded || 0,
      failed: counts.Failed || 0,
      unknown: counts.Unknown || 0,
    };
  }
}

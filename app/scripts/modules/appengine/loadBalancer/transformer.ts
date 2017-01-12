import {module} from 'angular';
import {chain, get, has, camelCase, filter, mapValues, cloneDeep} from 'lodash';

import {InstanceCounts, ILoadBalancer, ServerGroup, Instance} from 'core/domain/index';
import {IAppengineLoadBalancer, IAppengineTrafficSplit} from 'appengine/domain/index';

export class AppengineLoadBalancerUpsertDescription {
  public credentials: string;
  public loadBalancerName: string;
  public name: string;
  public split: IAppengineTrafficSplit;
  public migrateTraffic: boolean;
  public region: string;

  constructor(loadBalancer: IAppengineLoadBalancer) {
    this.credentials = loadBalancer.account;
    this.loadBalancerName = loadBalancer.name;
    this.name = loadBalancer.name;
    this.split = cloneDeep(loadBalancer.split);
    this.split.allocations = mapValues(this.split.allocations, (percent: number) => percent / 100);
    this.region = loadBalancer.region;
    this.migrateTraffic = loadBalancer.migrateTraffic || false;
  }
}

export class AppengineLoadBalancerTransformer {
  static get $inject() { return ['$q']; }

  constructor(private $q: ng.IQService) {}

  public normalizeLoadBalancer(loadBalancer: ILoadBalancer): ng.IPromise<ILoadBalancer> {
    loadBalancer.provider = loadBalancer.type;
    loadBalancer.instanceCounts = this.buildInstanceCounts(loadBalancer.serverGroups);
    loadBalancer.instances = [];
    loadBalancer.serverGroups.forEach((serverGroup) => {
      serverGroup.account = loadBalancer.account;
      serverGroup.region = loadBalancer.region;

      if (serverGroup.detachedInstances) {
        serverGroup.detachedInstances = serverGroup.detachedInstances.map((id: string) => ({id}));
      }
      serverGroup.instances = serverGroup.instances
        .concat(serverGroup.detachedInstances || [])
        .map((instance: any) => this.transformInstance(instance, loadBalancer));
    });

    let activeServerGroups = filter(loadBalancer.serverGroups, {isDisabled: false});
    loadBalancer.instances = chain(activeServerGroups).map('instances').flatten().value() as Instance[];
    return this.$q.resolve(loadBalancer);
  }

  public convertLoadBalancerForEditing(loadBalancer: IAppengineLoadBalancer): IAppengineLoadBalancer {
    loadBalancer.split.allocations = mapValues(loadBalancer.split.allocations, (decimal: number) => decimal * 100);
    return loadBalancer;
  }

  public convertLoadBalancerToUpsertDescription(loadBalancer: IAppengineLoadBalancer): AppengineLoadBalancerUpsertDescription {
    return new AppengineLoadBalancerUpsertDescription(loadBalancer);
  }

  private buildInstanceCounts(serverGroups: ServerGroup[]): InstanceCounts {
    let instanceCounts: InstanceCounts = chain(serverGroups)
      .map('instances')
      .flatten()
      .reduce((acc: InstanceCounts, instance: any) => {
        if (has(instance, 'health.state')) {
          acc[camelCase(instance.health.state)]++;
        }
        return acc;
      }, {up: 0, down: 0, outOfService: 0, succeeded: 0, failed: 0, unknown: 0}).value();

    instanceCounts.outOfService += chain(serverGroups).map('detachedInstances').flatten().value().length;
    return instanceCounts;
  }

  private transformInstance(instance: any, loadBalancer: ILoadBalancer) {
    instance.provider = loadBalancer.type;
    instance.account = loadBalancer.account;
    instance.region = loadBalancer.region;
    instance.loadBalancers = [loadBalancer.name];
    let health = instance.health || {};
    instance.healthState = get(instance, 'health.state') || 'OutOfService';
    instance.health = [health];

    return instance as Instance;
  }
}

export const APPENGINE_LOAD_BALANCER_TRANSFORMER = 'spinnaker.appengine.loadBalancer.transformer.service';

module(APPENGINE_LOAD_BALANCER_TRANSFORMER, [])
  .service('appengineLoadBalancerTransformer', AppengineLoadBalancerTransformer);

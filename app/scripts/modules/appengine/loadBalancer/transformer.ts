import {module} from 'angular';
import {chain, get, has, camelCase, filter, cloneDeep, reduce} from 'lodash';

import {InstanceCounts, ILoadBalancer, ServerGroup, Instance} from 'core/domain/index';
import {IAppengineLoadBalancer, IAppengineTrafficSplit, ShardBy} from 'appengine/domain/index';
import {ILoadBalancerUpsertDescription} from 'core/loadBalancer/loadBalancer.write.service';
import {Application} from 'core/application/application.model';

export interface IAppengineAllocationDescription {
  serverGroupName?: string;
  target?: string;
  cluster?: string;
  allocation: number;
  locatorType: 'fromExisting' | 'targetCoordinate' | 'text';
}

export interface IAppengineTrafficSplitDescription {
  shardBy: ShardBy;
  allocationDescriptions: IAppengineAllocationDescription[];
}

export class AppengineLoadBalancerUpsertDescription implements ILoadBalancerUpsertDescription, IAppengineLoadBalancer {
  public credentials: string;
  public account: string;
  public loadBalancerName: string;
  public name: string;
  public splitDescription: IAppengineTrafficSplitDescription;
  public split?: IAppengineTrafficSplit;
  public migrateTraffic: boolean;
  public region: string;
  public cloudProvider: string;
  public serverGroups?: any[];

  public static convertTrafficSplitToTrafficSplitDescription(split: IAppengineTrafficSplit): IAppengineTrafficSplitDescription {
    const allocationDescriptions = reduce(split.allocations, (acc: IAppengineAllocationDescription[], allocation: number, serverGroupName: string) => {
       return acc.concat({serverGroupName, allocation, locatorType: 'fromExisting'});
    }, []);

    return {shardBy: split.shardBy, allocationDescriptions};
  }

  constructor(loadBalancer: IAppengineLoadBalancer) {
    this.credentials = loadBalancer.account || loadBalancer.credentials;
    this.account = this.credentials;
    this.cloudProvider = loadBalancer.cloudProvider;
    this.loadBalancerName = loadBalancer.name;
    this.name = loadBalancer.name;
    this.region = loadBalancer.region;
    this.migrateTraffic = loadBalancer.migrateTraffic || false;
    this.serverGroups = loadBalancer.serverGroups;
  }

  public mapAllocationsToDecimals() {
    this.splitDescription.allocationDescriptions.forEach((description) => {
      description.allocation = description.allocation / 100;
    });
  }

  public mapAllocationsToPercentages() {
    this.splitDescription.allocationDescriptions.forEach((description) => {
      // An allocation percent has at most one decimal place.
       description.allocation = Math.round(description.allocation * 1000) / 10;
    });
  }
}

export class AppengineLoadBalancerTransformer {
  constructor(private $q: ng.IQService) { 'ngInject'; }

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

    const activeServerGroups = filter(loadBalancer.serverGroups, {isDisabled: false});
    loadBalancer.instances = chain(activeServerGroups).map('instances').flatten().value() as Instance[];
    return this.$q.resolve(loadBalancer);
  }

  public convertLoadBalancerForEditing(loadBalancer: IAppengineLoadBalancer,
                                       application: Application): ng.IPromise<IAppengineLoadBalancer> {
    return application.getDataSource('loadBalancers').ready().then(() => {
      const upToDateLoadBalancer = application.getDataSource('loadBalancers').data.find((candidate: ILoadBalancer) => {
        return candidate.name === loadBalancer.name &&
          (candidate.account === loadBalancer.account || candidate.account === loadBalancer.credentials);
      });

      if (upToDateLoadBalancer) {
        loadBalancer.serverGroups = cloneDeep(upToDateLoadBalancer.serverGroups);
      }
      return loadBalancer;
    });
  }

  public convertLoadBalancerToUpsertDescription(loadBalancer: IAppengineLoadBalancer): AppengineLoadBalancerUpsertDescription {
    return new AppengineLoadBalancerUpsertDescription(loadBalancer);
  }

  private buildInstanceCounts(serverGroups: ServerGroup[]): InstanceCounts {
    const instanceCounts: InstanceCounts = chain(serverGroups)
      .map('instances')
      .flatten()
      .reduce((acc: InstanceCounts, instance: any) => {
        if (has(instance, 'health.state')) {
          acc[camelCase(instance.health.state)]++;
        }
        return acc;
      }, {up: 0, down: 0, outOfService: 0, succeeded: 0, failed: 0, starting: 0, unknown: 0}).value();

    instanceCounts.outOfService += chain(serverGroups).map('detachedInstances').flatten().value().length;
    return instanceCounts;
  }

  private transformInstance(instance: any, loadBalancer: ILoadBalancer) {
    instance.provider = loadBalancer.type;
    instance.account = loadBalancer.account;
    instance.region = loadBalancer.region;
    instance.loadBalancers = [loadBalancer.name];
    const health = instance.health || {};
    instance.healthState = get(instance, 'health.state') || 'OutOfService';
    instance.health = [health];

    return instance as Instance;
  }
}

export const APPENGINE_LOAD_BALANCER_TRANSFORMER = 'spinnaker.appengine.loadBalancer.transformer.service';

module(APPENGINE_LOAD_BALANCER_TRANSFORMER, [])
  .service('appengineLoadBalancerTransformer', AppengineLoadBalancerTransformer);

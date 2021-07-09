import { camelCase, chain, cloneDeep, has } from 'lodash';

import {
  Application,
  IInstance,
  IInstanceCounts,
  ILoadBalancer,
  ILoadBalancerUpsertCommand,
  IServerGroup,
} from '@spinnaker/core';
import {
  ICloudFoundryLoadBalancer,
  ICloudFoundryLoadBalancerUpsertCommand,
  ICloudFoundryServerGroup,
  ICloudFoundrySpace,
} from '../domain';

export class CloudFoundryLoadBalancerUpsertDescription
  implements ILoadBalancerUpsertCommand, ICloudFoundryLoadBalancer {
  public id: string;
  public credentials: string;
  public account: string;
  public loadBalancerName: string;
  public name: string;
  public region: string;
  public cloudProvider: string;
  public serverGroups: ICloudFoundryServerGroup[];
  public space: ICloudFoundrySpace;

  constructor(loadBalancer: ICloudFoundryLoadBalancer) {
    this.id = loadBalancer.id;
    this.credentials = loadBalancer.account;
    this.account = this.credentials;
    this.cloudProvider = loadBalancer.cloudProvider;
    this.loadBalancerName = loadBalancer.name;
    this.name = loadBalancer.name;
    this.region = loadBalancer.region;
    this.serverGroups = loadBalancer.serverGroups;
    this.space = loadBalancer.space;
  }
}

export class CloudFoundryLoadBalancerTransformer {
  public static $inject = ['$q'];
  constructor(private $q: ng.IQService) {}

  public normalizeLoadBalancer(loadBalancer: ILoadBalancer): PromiseLike<ILoadBalancer> {
    loadBalancer.provider = loadBalancer.type;
    loadBalancer.instanceCounts = this.buildInstanceCounts(loadBalancer.serverGroups);
    loadBalancer.instances = [];
    loadBalancer.serverGroups.forEach((serverGroup) => {
      serverGroup.account = loadBalancer.account;
      serverGroup.region = loadBalancer.region;
      serverGroup.cloudProvider = loadBalancer.provider;

      if (serverGroup.detachedInstances) {
        serverGroup.detachedInstances = (serverGroup.detachedInstances as any).map((id: string) => ({ id }));
      }
      serverGroup.instances = serverGroup.instances
        .concat(serverGroup.detachedInstances || [])
        .map((instance: any) => this.transformInstance(instance, loadBalancer));
    });

    const activeServerGroups = loadBalancer.serverGroups.filter((sg) => !sg.isDisabled);
    loadBalancer.instances = chain(activeServerGroups).map('instances').flatten().value() as IInstance[];
    return this.$q.resolve(loadBalancer);
  }

  public constructNewCloudFoundryLoadBalancerTemplate(
    application: Application,
  ): ICloudFoundryLoadBalancerUpsertCommand {
    return {
      cloudProvider: 'cloudfoundry',
      credentials: undefined,
      name: application.name,
      id: undefined,
      host: '',
      path: '',
      port: '',
      region: '',
      domain: '',
      serverGroups: [],
      routes: [],
    };
  }

  public convertLoadBalancerForEditing(
    loadBalancer: ICloudFoundryLoadBalancer,
    application: Application,
  ): PromiseLike<ICloudFoundryLoadBalancer> {
    return application
      .getDataSource('loadBalancers')
      .ready()
      .then(() => {
        const upToDateLoadBalancer = application
          .getDataSource('loadBalancers')
          .data.find((candidate: ILoadBalancer) => {
            return candidate.name === loadBalancer.name && candidate.account === loadBalancer.account;
          });

        if (upToDateLoadBalancer) {
          loadBalancer.serverGroups = cloneDeep(upToDateLoadBalancer.serverGroups);
        }
        return loadBalancer;
      });
  }

  public convertLoadBalancerToUpsertDescription(
    loadBalancer: ICloudFoundryLoadBalancer,
  ): CloudFoundryLoadBalancerUpsertDescription {
    return new CloudFoundryLoadBalancerUpsertDescription(loadBalancer);
  }

  private buildInstanceCounts(serverGroups: IServerGroup[]): IInstanceCounts {
    const instanceCounts: IInstanceCounts = chain(serverGroups)
      .map('instances')
      .flatten()
      .reduce(
        (acc: IInstanceCounts, instance: any) => {
          if (has(instance, 'health.state')) {
            acc[camelCase(instance.health.state)]++;
          }
          return acc;
        },
        { up: 0, down: 0, outOfService: 0, succeeded: 0, failed: 0, starting: 0, unknown: 0 },
      )
      .value();

    instanceCounts.outOfService += chain(serverGroups).map('detachedInstances').flatten().value().length;
    return instanceCounts;
  }

  private transformInstance(instance: any, loadBalancer: ILoadBalancer) {
    instance.provider = loadBalancer.type;
    instance.account = loadBalancer.account;
    instance.region = loadBalancer.region;
    instance.loadBalancers = [loadBalancer.name];
    const health = instance.health || {};
    instance.healthState = health.state ?? 'OutOfService';
    instance.health = [health];

    return instance as IInstance;
  }
}

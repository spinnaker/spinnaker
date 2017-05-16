import {module} from 'angular';

import {API_SERVICE, Api} from 'core/api/api.service';
import {INFRASTRUCTURE_CACHE_SERVICE, InfrastructureCacheService} from 'core/cache/infrastructureCaches.service';
import {NAMING_SERVICE, NamingService, IComponentName} from 'core/naming/naming.service';
import {ILoadBalancer} from 'core/domain';

export interface ILoadBalancersByRegion {
  name: string;
  loadBalancers: ILoadBalancer[];
}

export interface ILoadBalancersByAccount {
  name: string;
  accounts: ILoadBalancersByRegion[];
}

export class LoadBalancerReader {

  public constructor(private $q: ng.IQService, private API: Api, private namingService: NamingService,
                     private loadBalancerTransformer: any, private infrastructureCaches: InfrastructureCacheService) {
    'ngInject';
  }

  public loadLoadBalancers(applicationName: string): ng.IPromise<ILoadBalancer[]> {
    return this.API.one('applications', applicationName).all('loadBalancers').getList()
      .then((loadBalancers: ILoadBalancer[]) => {
        loadBalancers = this.loadBalancerTransformer.normalizeLoadBalancerSet(loadBalancers);
        return this.$q.all(loadBalancers.map(lb => this.normalizeLoadBalancer(lb)));
      });
  }

  public getLoadBalancerDetails(cloudProvider: string, account: string, region: string, name: string): ng.IPromise<ILoadBalancer> {
    return this.API.all('loadBalancers').all(account).all(region).all(name).withParams({'provider': cloudProvider}).get();
  }

  public listLoadBalancers(cloudProvider: string): ng.IPromise<ILoadBalancersByAccount[]> {
    return this.API.all('loadBalancers')
      .useCache(this.infrastructureCaches.get('loadBalancers'))
      .withParams({provider: cloudProvider})
      .getList();
  }

  private normalizeLoadBalancer(loadBalancer: ILoadBalancer): ng.IPromise<ILoadBalancer> {
    return this.loadBalancerTransformer.normalizeLoadBalancer(loadBalancer).then((lb: ILoadBalancer) => {
      const nameParts: IComponentName = this.namingService.parseLoadBalancerName(lb.name);
      lb.stack = nameParts.stack;
      lb.detail = nameParts.freeFormDetails;
      lb.cloudProvider = lb.cloudProvider || lb.type || lb.provider;
      return lb;
    });
  }

}

export const LOAD_BALANCER_READ_SERVICE = 'spinnaker.core.loadBalancer.read.service';

module(LOAD_BALANCER_READ_SERVICE, [
  NAMING_SERVICE,
  INFRASTRUCTURE_CACHE_SERVICE,
  require('./loadBalancer.transformer.js'),
  API_SERVICE
]).service('loadBalancerReader', LoadBalancerReader);

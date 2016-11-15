import {module} from 'angular';
import {API_SERVICE, Api} from 'core/api/api.service';

export interface ISubnet {
  id: string;
  name: string;
  account: string;
  region: string;
  type: string;
  label: string;
  purpose: string;
  deprecated: boolean;
}

export class SubnetReader {

  private cachedSubnets: ISubnet[];

  static get $inject() { return ['$q', 'API', 'infrastructureCaches']; }

  public constructor(private $q: ng.IQService, private API: Api, private infrastructureCaches: any) {}

  public listSubnets(): ng.IPromise<ISubnet[]> {
    if (this.cachedSubnets) {
      return this.$q.when(this.cachedSubnets);
    }
    return this.API.one('subnets')
      .useCache(this.infrastructureCaches.subnets)
      .getList()
      .then((subnets: ISubnet[]) => {
        subnets.forEach((subnet: ISubnet) => {
          subnet.label = subnet.purpose;
          subnet.deprecated = !!subnet.deprecated;
          if (subnet.deprecated) {
            subnet.label += ' (deprecated)';
          }
        });
        this.cachedSubnets = subnets;
        return subnets;
      });
  }

  public listSubnetsByProvider(cloudProvider: string): ng.IPromise<ISubnet[]> {
    return this.API.one('subnets', cloudProvider)
      .useCache(this.infrastructureCaches.subnets)
      .getList();
  }

  public getSubnetByIdAndProvider(subnetId: string, cloudProvider = 'aws'): ng.IPromise<ISubnet> {
    return this.listSubnetsByProvider(cloudProvider)
      .then((subnets: ISubnet[]) => {
        return subnets.find(subnet => subnet.id === subnetId);
      });
  }

  public getSubnetPurpose(subnetId: string): ng.IPromise<string> {
    return this.listSubnets().then((subnets: ISubnet[]) => {
      const match: ISubnet = subnets.find(test => test.id === subnetId);
      return match ? match.purpose : null;
    });
  }
}

export const SUBNET_READ_SERVICE = 'spinnaker.core.subnet.read.service';

module(SUBNET_READ_SERVICE, [
  API_SERVICE,
  require('../cache/infrastructureCaches')
]).service('subnetReader', SubnetReader);

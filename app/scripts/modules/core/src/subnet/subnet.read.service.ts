import {module} from 'angular';
import {INFRASTRUCTURE_CACHE_SERVICE, InfrastructureCacheService} from 'core/cache/infrastructureCaches.service';
import {API_SERVICE, Api} from 'core/api/api.service';
import { ISubnet } from 'core/domain';

export class SubnetReader {

  private static NAMESPACE = 'subnets';

  public constructor(private API: Api, private infrastructureCaches: InfrastructureCacheService) { 'ngInject'; }

  public listSubnets(): ng.IPromise<ISubnet[]> {
    return this.API.one('subnets')
      .useCache(this.infrastructureCaches.get(SubnetReader.NAMESPACE))
      .getList()
      .then((subnets: ISubnet[]) => {
        subnets.forEach((subnet: ISubnet) => {
          subnet.label = subnet.purpose;
          subnet.deprecated = !!subnet.deprecated;
          if (subnet.deprecated) {
            subnet.label += ' (deprecated)';
          }
        });
        return subnets;
      });
  }

  public listSubnetsByProvider(cloudProvider: string): ng.IPromise<ISubnet[]> {
    return this.API.one('subnets', cloudProvider)
      .useCache(this.infrastructureCaches.get(SubnetReader.NAMESPACE))
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
module(SUBNET_READ_SERVICE, [API_SERVICE, INFRASTRUCTURE_CACHE_SERVICE])
  .service('subnetReader', SubnetReader);

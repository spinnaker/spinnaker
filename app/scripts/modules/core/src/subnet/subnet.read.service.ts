import { module } from 'angular';
import { InfrastructureCaches } from 'core/cache/infrastructureCaches';
import { API } from 'core/api/ApiService';
import { ISubnet } from 'core/domain';

export class SubnetReader {
  private static NAMESPACE = 'subnets';

  public listSubnets(): ng.IPromise<ISubnet[]> {
    return API.one('subnets')
      .useCache(InfrastructureCaches.get(SubnetReader.NAMESPACE))
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
    return API.one('subnets', cloudProvider)
      .useCache(InfrastructureCaches.get(SubnetReader.NAMESPACE))
      .getList();
  }

  public getSubnetByIdAndProvider(subnetId: string, cloudProvider = 'aws'): ng.IPromise<ISubnet> {
    return this.listSubnetsByProvider(cloudProvider).then((subnets: ISubnet[]) => {
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
module(SUBNET_READ_SERVICE, []).service('subnetReader', SubnetReader);

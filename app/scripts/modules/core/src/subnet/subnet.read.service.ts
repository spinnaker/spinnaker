import { IPromise } from 'angular';
import { InfrastructureCaches } from 'core/cache/infrastructureCaches';
import { API } from 'core/api/ApiService';
import { ISubnet } from 'core/domain';

export class SubnetReader {
  private static NAMESPACE = 'subnets';

  public static listSubnets(): IPromise<ISubnet[]> {
    return API.one('subnets')
      .useCache(InfrastructureCaches.get(this.NAMESPACE))
      .getList()
      .then((subnets: ISubnet[]) => {
        subnets.forEach((subnet: ISubnet) => {
          subnet.label = subnet.purpose;
          subnet.deprecated = !!subnet.deprecated;
          if (subnet.deprecated) {
            subnet.label += ' (deprecated)';
          }
        });
        return subnets.filter(s => s.label);
      });
  }

  public static listSubnetsByProvider(cloudProvider: string): ng.IPromise<ISubnet[]> {
    return API.one('subnets', cloudProvider)
      .useCache(InfrastructureCaches.get(this.NAMESPACE))
      .getList();
  }

  public static getSubnetByIdAndProvider(subnetId: string, cloudProvider = 'aws'): ng.IPromise<ISubnet> {
    return this.listSubnetsByProvider(cloudProvider).then((subnets: ISubnet[]) => {
      return subnets.find(subnet => subnet.id === subnetId);
    });
  }

  public static getSubnetPurpose(subnetId: string): ng.IPromise<string> {
    return this.listSubnets().then((subnets: ISubnet[]) => {
      const match: ISubnet = subnets.find(test => test.id === subnetId);
      return match ? match.purpose : null;
    });
  }
}


import { API } from 'core/api/ApiService';
import { ISubnet } from 'core/domain';

export class SubnetReader {
  private static cache: PromiseLike<ISubnet[]>;

  public static listSubnets(): PromiseLike<ISubnet[]> {
    if (this.cache) {
      return this.cache;
    }
    this.cache = API.one('subnets')
      .getList()
      .then((subnets: ISubnet[]) => {
        subnets.forEach((subnet: ISubnet) => {
          subnet.label = subnet.purpose;
          subnet.deprecated = !!subnet.deprecated;
          if (subnet.deprecated) {
            subnet.label += ' (deprecated)';
          }
        });
        return subnets.filter((s) => s.label);
      });
    return this.cache;
  }

  public static listSubnetsByProvider(cloudProvider: string): PromiseLike<ISubnet[]> {
    return API.one('subnets', cloudProvider).getList();
  }

  public static getSubnetByIdAndProvider(subnetId: string, cloudProvider = 'aws'): PromiseLike<ISubnet> {
    return this.listSubnetsByProvider(cloudProvider).then((subnets: ISubnet[]) => {
      return subnets.find((subnet) => subnet.id === subnetId);
    });
  }

  public static getSubnetPurpose(subnetId: string): PromiseLike<string> {
    return this.listSubnets().then((subnets: ISubnet[]) => {
      const match: ISubnet = subnets.find((test) => test.id === subnetId);
      return match ? match.purpose : null;
    });
  }
}

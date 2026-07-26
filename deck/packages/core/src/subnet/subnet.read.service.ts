import { REST } from '../api/ApiService';
import type { ISubnet } from '../domain';

export class SubnetReader {
  private static cache: Promise<ISubnet[]>;

  public static listSubnets(): Promise<ISubnet[]> {
    if (this.cache) {
      return this.cache;
    }
    this.cache = REST('/subnets')
      .get()
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

  public static listSubnetsByProvider(cloudProvider: string): Promise<ISubnet[]> {
    return REST('/subnets').path(cloudProvider).get();
  }

  public static getSubnetByIdAndProvider(subnetId: string, cloudProvider = 'aws'): Promise<ISubnet> {
    return this.listSubnetsByProvider(cloudProvider).then((subnets: ISubnet[]) => {
      return subnets.find((subnet) => subnet.id === subnetId);
    });
  }

  public static getSubnetPurpose(subnetId: string): Promise<string> {
    return this.listSubnets().then((subnets: ISubnet[]) => {
      const match: ISubnet = subnets.find((test) => test.id === subnetId);
      return match ? match.purpose : null;
    });
  }
}

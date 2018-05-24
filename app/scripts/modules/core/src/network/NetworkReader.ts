import { InfrastructureCaches } from 'core/cache';
import { API } from 'core/api/ApiService';

export interface INetwork {
  cloudProvider: string;
  id: string;
  name: string;
  account: string;
  region: string;
  deprecated: boolean;
}

export class NetworkReader {
  public static listNetworks(): INetwork[] {
    return API.one('networks')
      .useCache(InfrastructureCaches.get('networks'))
      .getList();
  }

  public static listNetworksByProvider(cloudProvider: string) {
    return API.one('networks')
      .one(cloudProvider)
      .useCache(InfrastructureCaches.get('networks'))
      .getList();
  }
}

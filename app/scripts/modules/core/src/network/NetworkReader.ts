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
  public static listNetworks(): PromiseLike<INetwork[]> {
    return API.path('networks').get();
  }

  public static listNetworksByProvider(cloudProvider: string) {
    return API.path('networks').path(cloudProvider).get();
  }
}

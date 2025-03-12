import { REST } from '../api/ApiService';

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
    return REST('/networks').get();
  }

  public static listNetworksByProvider(cloudProvider: string) {
    return REST('/networks').path(cloudProvider).get();
  }
}

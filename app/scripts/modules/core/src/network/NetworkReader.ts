import { REST } from 'core/api/ApiService';

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
    return REST().path('networks').get();
  }

  public static listNetworksByProvider(cloudProvider: string) {
    return REST().path('networks', cloudProvider).get();
  }
}

import { module } from 'angular';

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
  public listNetworks(): INetwork[] {
    return API.one('networks')
      .useCache(InfrastructureCaches.get('networks'))
      .getList();
  }

  public listNetworksByProvider(cloudProvider: string) {
    return API.one('networks')
      .one(cloudProvider)
      .useCache(InfrastructureCaches.get('networks'))
      .getList();
  }
}

export const NETWORK_READ_SERVICE = 'spinnaker.core.network.read.service';
module(NETWORK_READ_SERVICE, []).service('networkReader', NetworkReader);

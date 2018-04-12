import { module } from 'angular';

import { InfrastructureCaches } from 'core/cache';
import { API_SERVICE, Api } from 'core/api/api.service';

export interface INetwork {
  cloudProvider: string;
  id: string;
  name: string;
  account: string;
  region: string;
  deprecated: boolean;
}

export class NetworkReader {
  public constructor(private API: Api) {
    'ngInject';
  }

  public listNetworks(): INetwork[] {
    return this.API.one('networks')
      .useCache(InfrastructureCaches.get('networks'))
      .getList();
  }

  public listNetworksByProvider(cloudProvider: string) {
    return this.API.one('networks')
      .one(cloudProvider)
      .useCache(InfrastructureCaches.get('networks'))
      .getList();
  }
}

export const NETWORK_READ_SERVICE = 'spinnaker.core.network.read.service';
module(NETWORK_READ_SERVICE, [API_SERVICE]).service('networkReader', NetworkReader);

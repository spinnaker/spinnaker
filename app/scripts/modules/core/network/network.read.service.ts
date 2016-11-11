import {module} from 'angular';

import {API_SERVICE, Api} from 'core/api/api.service';

export interface INetwork {
  cloudProvider: string;
  id: string;
  name: string;
  account: string;
  region: string;
  deprecated: boolean;
}

export class NetworkReader {

  static get $inject() { return ['API', 'infrastructureCaches']; }

  public constructor(private API: Api, private infrastructureCaches: any) {}

  public listNetworks(): INetwork[] {
    return this.API.one('networks')
      .useCache(this.infrastructureCaches.networks)
      .getList();
  }

  public listNetworksByProvider(cloudProvider: string ) {
    return this.API.one('networks').one(cloudProvider)
      .useCache(this.infrastructureCaches.networks)
      .getList();
  }
}

export const NETWORK_READ_SERVICE = 'spinnaker.core.network.read.service';
module(NETWORK_READ_SERVICE, [
  API_SERVICE,
  require('../cache/infrastructureCaches.js')
]).service('networkReader', NetworkReader);

import {module} from 'angular';

import {INFRASTRUCTURE_CACHE_SERVICE, InfrastructureCacheService} from 'core/cache/infrastructureCaches.service';
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

  public constructor(private API: Api,
                     private infrastructureCaches: InfrastructureCacheService) {}

  public listNetworks(): INetwork[] {
    return this.API.one('networks')
      .useCache(this.infrastructureCaches.get('networks'))
      .getList();
  }

  public listNetworksByProvider(cloudProvider: string ) {
    return this.API.one('networks').one(cloudProvider)
      .useCache(this.infrastructureCaches.get('networks'))
      .getList();
  }
}

export const NETWORK_READ_SERVICE = 'spinnaker.core.network.read.service';
module(NETWORK_READ_SERVICE, [API_SERVICE, INFRASTRUCTURE_CACHE_SERVICE])
  .service('networkReader', NetworkReader);

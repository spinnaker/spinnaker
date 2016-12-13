import {module} from 'angular';

import {INFRASTRUCTURE_CACHE_SERVICE, InfrastructureCacheService} from 'core/cache/infrastructureCaches.service';
import {API_SERVICE, Api} from 'core/api/api.service';
import {IGceHealthCheck} from '../domain';

export class GceHealthCheckReader {
  static get $inject () { return ['API', 'infrastructureCaches']; }

  constructor (public API: Api, public infrastructureCaches: InfrastructureCacheService) {}

  listHealthChecks (type?: string): ng.IPromise<IGceHealthCheck[]> {
    if (type) {
      return this.listHealthChecks()
        .then((healthChecks: IGceHealthCheck[]) => {
          return healthChecks
            .filter((healthCheck) => healthCheck.healthCheckType === type);
        });
    } else {
      return this.API
        .all('search')
        .useCache(this.infrastructureCaches.get('healthChecks'))
        .getList({q: '', type: 'healthChecks'})
        .then((searchEndPointWrapper: any[]) => {
          let healthCheckWrappers = searchEndPointWrapper[0].results;
          return healthCheckWrappers.map((wrapper: any) => {
            wrapper.healthCheck = JSON.parse(wrapper.healthCheck);
            wrapper.healthCheck.account = wrapper.account;
            return wrapper.healthCheck as IGceHealthCheck;
          });
        });
    }
  }
}

export const GCE_HEALTH_CHECK_READER = 'spinnaker.gce.healthCheck.reader';
module(GCE_HEALTH_CHECK_READER, [API_SERVICE, INFRASTRUCTURE_CACHE_SERVICE])
  .service('gceHealthCheckReader', GceHealthCheckReader);

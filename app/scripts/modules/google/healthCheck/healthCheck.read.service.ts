import {module} from 'angular';

import {API_SERVICE, Api} from 'core/api/api.service';
import {IGceHealthCheck} from '../domain';

export class GceHealthCheckReader {
  static get $inject () { return ['API', 'infrastructureCaches']; }

  constructor (public API: Api, public infrastructureCaches: any) {}

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
        .useCache(this.infrastructureCaches.healthChecks)
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

module(GCE_HEALTH_CHECK_READER, [
  API_SERVICE,
  require('core/cache/infrastructureCaches.js'),
]).service('gceHealthCheckReader', GceHealthCheckReader);

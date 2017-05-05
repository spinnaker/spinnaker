import {module} from 'angular';
import {uniqWith} from 'lodash';

interface IHealthCheckSearchResults {
  name: string;
  account: string;
  healthCheck: string; // JSON encoded string containing the real health check (it is missing the account).
  kind: string;
  provider: string;
  type: string;
}

import {INFRASTRUCTURE_CACHE_SERVICE, InfrastructureCacheService} from 'core/cache/infrastructureCaches.service';
import {SEARCH_SERVICE, SearchService, ISearchResults} from 'core/search/search.service';
import {IGceHealthCheck} from 'google/domain';

export class GceHealthCheckReader {
  constructor (private searchService: SearchService, private infrastructureCaches: InfrastructureCacheService) { 'ngInject'; }

  public listHealthChecks (type?: string): ng.IPromise<IGceHealthCheck[]> {
    if (type) {
      return this.listHealthChecks().then(healthChecks => healthChecks.filter(healthCheck => healthCheck.healthCheckType === type));
    } else {
      return this.searchService
        .search({q: '', type: 'healthChecks'}, this.infrastructureCaches.get('healthChecks'))
        .then((searchResults: ISearchResults<IHealthCheckSearchResults>) => {
          if (searchResults && searchResults.results) {
            const healthChecks = searchResults.results.filter(result => result.provider === 'gce')
              .map(result => {
                const healthCheck = JSON.parse(result.healthCheck) as IGceHealthCheck;
                healthCheck.account = result.account;
                return healthCheck;
              });
            return uniqWith(healthChecks, (checkA: IGceHealthCheck, checkB: IGceHealthCheck) => {
              return checkA.name === checkB.name && checkA.healthCheckType === checkB.healthCheckType &&
                  checkA.account === checkB.account;
            });
          } else {
            return [];
          }
        })
        .catch(() => []);
    }
  }
}

export const GCE_HEALTH_CHECK_READER = 'spinnaker.gce.healthCheck.reader';
module(GCE_HEALTH_CHECK_READER, [SEARCH_SERVICE, INFRASTRUCTURE_CACHE_SERVICE])
  .service('gceHealthCheckReader', GceHealthCheckReader);

import { module } from 'angular';
import { uniqWith } from 'lodash';

import { InfrastructureCaches, ISearchResults, SearchService } from '@spinnaker/core';
import { IGceHealthCheck } from '../domain';

interface IHealthCheckSearchResults {
  name: string;
  account: string;
  healthCheck: string; // JSON encoded string containing the real health check (it is missing the account).
  region?: string;
  kind: string;
  provider: string;
  type: string;
}

export class GceHealthCheckReader {
  public listHealthChecks(type?: string): PromiseLike<IGceHealthCheck[]> {
    if (type) {
      return this.listHealthChecks().then((healthChecks) =>
        healthChecks.filter((healthCheck) => healthCheck.healthCheckType === type),
      );
    } else {
      return SearchService.search(
        { q: '', type: 'healthChecks', allowShortQuery: 'true' },
        InfrastructureCaches.get('healthChecks'),
      )
        .then((searchResults: ISearchResults<IHealthCheckSearchResults>) => {
          if (searchResults && searchResults.results) {
            const healthChecks = searchResults.results
              .filter((result) => result.provider === 'gce')
              .map((result) => {
                const healthCheck = JSON.parse(result.healthCheck) as IGceHealthCheck;
                healthCheck.account = result.account;
                return healthCheck;
              });
            return uniqWith(healthChecks, (checkA: IGceHealthCheck, checkB: IGceHealthCheck) => {
              return (
                checkA.name === checkB.name &&
                checkA.healthCheckType === checkB.healthCheckType &&
                checkA.account === checkB.account
              );
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
module(GCE_HEALTH_CHECK_READER, []).service('gceHealthCheckReader', GceHealthCheckReader);
